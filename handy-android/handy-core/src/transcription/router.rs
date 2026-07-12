use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{mpsc, Arc, Mutex};

pub enum StreamCmd {
    Feed(Vec<f32>),
    Finalize,
    Cancel,
}

pub struct StreamRouter {
    tx: Mutex<Option<mpsc::Sender<StreamCmd>>>,
    open: Arc<AtomicBool>,
}

impl StreamRouter {
    pub fn new() -> Self {
        Self {
            tx: Mutex::new(None),
            open: Arc::new(AtomicBool::new(false)),
        }
    }

    /// Zero-cost when not active: single relaxed atomic load
    pub fn feed(&self, frame: Vec<f32>) {
        if !self.open.load(Ordering::Relaxed) {
            return;
        }
        if let Some(tx) = self.tx.lock().unwrap().as_ref() {
            let _ = tx.send(StreamCmd::Feed(frame));
        }
    }

    pub fn is_open(&self) -> bool {
        self.open.load(Ordering::Relaxed)
    }

    pub fn open_channel(&self) -> mpsc::Receiver<StreamCmd> {
        let (tx, rx) = mpsc::channel();
        *self.tx.lock().unwrap() = Some(tx);
        self.open.store(true, Ordering::Release);
        rx
    }

    pub fn send(&self, cmd: StreamCmd) -> Result<(), ()> {
        if let Some(tx) = self.tx.lock().unwrap().as_ref() {
            tx.send(cmd).map_err(|_| ())
        } else {
            Err(())
        }
    }

    pub fn close(&self) {
        self.open.store(false, Ordering::Release);
        *self.tx.lock().unwrap() = None;
    }
}

impl Default for StreamRouter {
    fn default() -> Self {
        Self::new()
    }
}
