use jni::objects::{GlobalRef, JObject, JValue};
use jni::JNIEnv;
use log::warn;

pub fn dispatch_state_change(env: &mut JNIEnv, callback: &GlobalRef, state: i32) {
    if let Err(e) = env.call_method(
        callback.as_obj(),
        "onStateChange",
        "(I)V",
        &[JValue::Int(state)],
    ) {
        warn!("dispatch_state_change failed: {e}");
    }
}

pub fn dispatch_transcription(
    env: &mut JNIEnv,
    callback: &GlobalRef,
    text: &str,
    is_partial: bool,
) {
    let j_text = match env.new_string(text) {
        Ok(s) => s,
        Err(e) => {
            warn!("dispatch_transcription: failed to create string: {e}");
            return;
        }
    };
    if let Err(e) = env.call_method(
        callback.as_obj(),
        "onTranscription",
        "(Ljava/lang/String;Z)V",
        &[JValue::Object(&j_text), JValue::Bool(is_partial as u8)],
    ) {
        warn!("dispatch_transcription failed: {e}");
    }
}

pub fn dispatch_vad_level(env: &mut JNIEnv, callback: &GlobalRef, level: f32) {
    if let Err(e) = env.call_method(
        callback.as_obj(),
        "onVadLevel",
        "(F)V",
        &[JValue::Float(level)],
    ) {
        warn!("dispatch_vad_level failed: {e}");
    }
}

pub fn dispatch_error(env: &mut JNIEnv, callback: &GlobalRef, code: i32, message: &str) {
    let j_message = match env.new_string(message) {
        Ok(s) => s,
        Err(e) => {
            warn!("dispatch_error: failed to create string: {e}");
            return;
        }
    };
    if let Err(e) = env.call_method(
        callback.as_obj(),
        "onError",
        "(ILjava/lang/String;)V",
        &[JValue::Int(code), JValue::Object(&j_message)],
    ) {
        warn!("dispatch_error failed: {e}");
    }
}

pub fn dispatch_download_progress(
    env: &mut JNIEnv,
    callback: &GlobalRef,
    model_id: &str,
    sofar: i64,
    total: i64,
) {
    let j_model_id = match env.new_string(model_id) {
        Ok(s) => s,
        Err(e) => {
            warn!("dispatch_download_progress: failed to create string: {e}");
            return;
        }
    };
    if let Err(e) = env.call_method(
        callback.as_obj(),
        "onDownloadProgress",
        "(Ljava/lang/String;JJ)V",
        &[
            JValue::Object(&j_model_id),
            JValue::Long(sofar),
            JValue::Long(total),
        ],
    ) {
        warn!("dispatch_download_progress failed: {e}");
    }
}

pub fn dispatch_download_complete(
    env: &mut JNIEnv,
    callback: &GlobalRef,
    model_id: &str,
    success: bool,
    error_msg: Option<&str>,
) {
    let j_model_id = match env.new_string(model_id) {
        Ok(s) => s,
        Err(e) => {
            warn!("dispatch_download_complete: failed to create string: {e}");
            return;
        }
    };
    let j_error_obj = match error_msg {
        Some(msg) => match env.new_string(msg) {
            Ok(s) => s.into(),
            Err(e) => {
                warn!("dispatch_download_complete: failed to create error string: {e}");
                return;
            }
        },
        None => JObject::null(),
    };
    if let Err(e) = env.call_method(
        callback.as_obj(),
        "onDownloadComplete",
        "(Ljava/lang/String;ZLjava/lang/String;)V",
        &[
            JValue::Object(&j_model_id),
            JValue::Bool(success as u8),
            JValue::Object(&j_error_obj),
        ],
    ) {
        warn!("dispatch_download_complete failed: {e}");
    }
}
