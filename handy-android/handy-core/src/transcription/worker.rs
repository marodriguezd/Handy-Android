use std::sync::mpsc;
use std::thread;
use std::time::Instant;

use crate::transcription::router::StreamCmd;

pub struct StreamWorker {
    worker_id: u64,
    handle: Option<thread::JoinHandle<()>>,
}

impl StreamWorker {
    pub fn new(worker_id: u64) -> Self {
        Self {
            worker_id,
            handle: None,
        }
    }

    pub fn worker_id(&self) -> u64 {
        self.worker_id
    }

    /// Spawn a background thread that processes streaming audio via transcribe-cpp.
    /// The thread takes ownership of the stream and feeds audio frames into it,
    /// emitting partial results on every committed/tentative change,
    /// and producing a final result on Finalize.
    pub fn spawn(
        &mut self,
        rx: mpsc::Receiver<StreamCmd>,
        mut stream: transcribe_cpp::Stream,
        on_partial: impl Fn(String) + Send + 'static,
        on_final: impl Fn(String) + Send + 'static,
    ) -> u64 {
        let worker_id = self.worker_id;

        let handle = thread::spawn(move || {
            for cmd in rx {
                match cmd {
                    StreamCmd::Feed(audio) => {
                        let update = match stream.feed(&audio) {
                            Ok(u) => u,
                            Err(e) => {
                                log::warn!("[handy-core] stream feed error: {e:?}");
                                continue;
                            }
                        };

                        if update.committed_changed || update.tentative_changed {
                            let t0 = Instant::now();
                            let text = stream.text().display().to_string();
                            if !text.is_empty() {
                                log::debug!("partial_latency_ms={}", t0.elapsed().as_millis());
                                on_partial(text);
                            }
                        }
                    }
                    StreamCmd::Finalize => {
                        match stream.finalize() {
                            Ok(_) => {
                                let final_t0 = Instant::now();
                                let text = stream.text().display().to_string();
                                log::debug!("finalize_latency_ms={}", final_t0.elapsed().as_millis());
                                on_final(text);
                            }
                            Err(e) => {
                                log::warn!("[handy-core] stream finalize error: {e:?}");
                                on_final(String::new());
                            }
                        }
                        break;
                    }
                    StreamCmd::Cancel => {
                        let _ = stream.reset();
                        break;
                    }
                }
            }
        });

        self.handle = Some(handle);
        worker_id
    }

    /// Join the worker thread, blocking until it completes.
    pub fn join(&mut self) {
        if let Some(handle) = self.handle.take() {
            let _ = handle.join();
        }
    }
}

impl Drop for StreamWorker {
    fn drop(&mut self) {
        self.join();
    }
}
