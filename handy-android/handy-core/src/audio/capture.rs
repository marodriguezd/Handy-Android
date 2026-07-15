use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

#[derive(thiserror::Error, Debug)]
pub enum CaptureError {
    #[error("AAudio error: {0}")]
    Aaudio(String),
    #[error("Stream already open")]
    AlreadyOpen,
    #[error("Stream not open")]
    NotOpen,
    #[error("Callback error: {0}")]
    Callback(String),
}

pub struct AudioCapture {
    stream_ptr: Option<*mut aaudio_sys::AAudioStream>,
    callback_ptr: Option<*mut std::ffi::c_void>,
    running: Arc<AtomicBool>,
}

unsafe impl Send for AudioCapture {}

impl AudioCapture {
    pub fn new() -> Self {
        Self {
            stream_ptr: None,
            callback_ptr: None,
            running: Arc::new(AtomicBool::new(false)),
        }
    }

    pub fn set_callback<F>(&mut self, cb: F)
    where
        F: FnMut(&[f32]) + Send + 'static,
    {
        if let Some(old) = self.callback_ptr.take() {
            let _ = unsafe { Box::from_raw(old as *mut Box<dyn FnMut(&[f32]) + Send>) };
        }
        self.callback_ptr = Some(
            Box::into_raw(Box::new(Box::new(cb) as Box<dyn FnMut(&[f32]) + Send>))
                as *mut std::ffi::c_void,
        );
    }

    /// Start capture and return the actual sample rate of the opened stream.
    /// Does NOT request a specific rate — uses the device's native rate and reports it back.
    ///
    /// NOTE: aaudio-sys v0.1.0 has a bug where DIRECTION_INPUT is set to 0 instead of 1.
    /// We use the raw value 1 here to ensure we actually open an input (capture) stream.
    pub fn start(&mut self) -> Result<u32, CaptureError> {
        if self.stream_ptr.is_some() {
            return Err(CaptureError::AlreadyOpen);
        }

        let user_data = self
            .callback_ptr
            .ok_or_else(|| CaptureError::Callback("No callback set".to_string()))?;

        let mut builder_ptr: *mut aaudio_sys::AAudioStreamBuilder = std::ptr::null_mut();
        let result = unsafe { aaudio_sys::AAudio_createStreamBuilder(&mut builder_ptr) };
        if result != aaudio_sys::OK {
            return Err(CaptureError::Aaudio(format!(
                "AAudio_createStreamBuilder failed: {}",
                result
            )));
        }

        unsafe {
            // CRITICAL: AAUDIO_DIRECTION_INPUT = 1 in actual AAudio API.
            // aaudio-sys v0.1.0 incorrectly defines both OUTPUT and INPUT as 0.
            // Using 1 directly to ensure we open a CAPTURE stream, not output.
            const AAUDIO_DIRECTION_INPUT: i32 = 1;
            aaudio_sys::AAudioStreamBuilder_setDirection(
                builder_ptr,
                AAUDIO_DIRECTION_INPUT,
            );

            aaudio_sys::AAudioStreamBuilder_setSharingMode(
                builder_ptr,
                aaudio_sys::SHARING_SHARED,
            );
            aaudio_sys::AAudioStreamBuilder_setFormat(
                builder_ptr,
                aaudio_sys::FORMAT_PCM_FLOAT,
            );
            // Do NOT request a specific sample rate — use device's native rate.
            // We'll query the actual rate after opening and resample to 16kHz.

            // Set input preset to VoiceRecognition for optimal speech capture.
            // This tells AAudio to use the microphone and optimize for speech
            // recognition quality.
            aaudio_sys::AAudioStreamBuilder_setInputPreset(
                builder_ptr,
                aaudio_sys::INPUT_PRESET_VOICE_RECOGNITION,
            );

            // Set content type to Speech for proper audio processing.
            aaudio_sys::AAudioStreamBuilder_setContentType(
                builder_ptr,
                aaudio_sys::CONTENT_TYPE_SPEECH,
            );

            // Use low-latency performance mode for responsive audio capture.
            aaudio_sys::AAudioStreamBuilder_setPerformanceMode(
                builder_ptr,
                aaudio_sys::PERFORMANCE_MODE_LOW_LATENCY,
            );

            aaudio_sys::AAudioStreamBuilder_setChannelCount(builder_ptr, 1);
            aaudio_sys::AAudioStreamBuilder_setDataCallback(
                builder_ptr,
                Some(Self::data_callback_thunk),
                user_data,
            );
            aaudio_sys::AAudioStreamBuilder_setErrorCallback(
                builder_ptr,
                Some(Self::error_callback_thunk),
                user_data,
            );
        }

        let mut stream_ptr: *mut aaudio_sys::AAudioStream = std::ptr::null_mut();
        let result = unsafe {
            aaudio_sys::AAudioStreamBuilder_openStream(builder_ptr, &mut stream_ptr)
        };

        unsafe {
            aaudio_sys::AAudioStreamBuilder_delete(builder_ptr);
        }

        if result != aaudio_sys::OK {
            return Err(CaptureError::Aaudio(format!(
                "AAudioStreamBuilder_openStream failed: {}",
                result
            )));
        }

        // Query the actual sample rate of the opened stream
        let actual_rate = unsafe { aaudio_sys::AAudioStream_getSampleRate(stream_ptr) };
        let actual_rate = if actual_rate > 0 { actual_rate as u32 } else { 48000 };
        log::info!("AAudio stream opened: {} Hz", actual_rate);

        let result = unsafe { aaudio_sys::AAudioStream_requestStart(stream_ptr) };
        if result != aaudio_sys::OK {
            unsafe {
                aaudio_sys::AAudioStream_close(stream_ptr);
            }
            return Err(CaptureError::Aaudio(format!(
                "AAudioStream_requestStart failed: {}",
                result
            )));
        }

        self.stream_ptr = Some(stream_ptr);
        self.running.store(true, Ordering::SeqCst);

        Ok(actual_rate as u32)
    }

    extern "C" fn data_callback_thunk(
        _stream: *mut aaudio_sys::AAudioStream,
        user_data: *mut std::ffi::c_void,
        audio_data: *mut std::ffi::c_void,
        num_frames: i32,
    ) -> i32 {
        if num_frames <= 0 || audio_data.is_null() {
            return aaudio_sys::CALLBACK_CONTINUE;
        }

        let cb = unsafe { &mut *(user_data as *mut Box<dyn FnMut(&[f32]) + Send>) };
        let samples =
            unsafe { std::slice::from_raw_parts(audio_data as *const f32, num_frames as usize) };
        cb(samples);

        aaudio_sys::CALLBACK_CONTINUE
    }

    extern "C" fn error_callback_thunk(
        _stream: *mut aaudio_sys::AAudioStream,
        _user_data: *mut std::ffi::c_void,
        error: i32,
    ) {
        log::error!("AAudio error callback: {}", error);
    }

    pub fn stop(&mut self) -> Result<(), CaptureError> {
        let ptr = self
            .stream_ptr
            .ok_or(CaptureError::NotOpen)?;

        let result = unsafe { aaudio_sys::AAudioStream_requestStop(ptr) };
        if result != aaudio_sys::OK {
            return Err(CaptureError::Aaudio(format!(
                "AAudioStream_requestStop failed: {}",
                result
            )));
        }

        self.running.store(false, Ordering::SeqCst);
        Ok(())
    }

    pub fn close(&mut self) -> Result<(), CaptureError> {
        if let Some(ptr) = self.stream_ptr.take() {
            unsafe {
                aaudio_sys::AAudioStream_close(ptr);
            }
        }
        self.running.store(false, Ordering::SeqCst);
        Ok(())
    }

    pub fn is_running(&self) -> bool {
        self.running.load(Ordering::SeqCst)
    }
}

impl Default for AudioCapture {
    fn default() -> Self {
        Self::new()
    }
}

impl Drop for AudioCapture {
    fn drop(&mut self) {
        let _ = self.close();
        if let Some(ptr) = self.callback_ptr.take() {
            let _ = unsafe { Box::from_raw(ptr as *mut Box<dyn FnMut(&[f32]) + Send>) };
        }
    }
}
