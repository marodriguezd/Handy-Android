use std::collections::HashMap;

use ort::ep;
use ort::inputs;
use ort::session::builder::GraphOptimizationLevel;
use ort::session::Session;
use ort::value::Tensor;

use super::mel_128;

const MAX_SEQUENCE_LENGTH: usize = 1024;
const MEL_DIM: usize = 128;

/// Maximum audio chunk size in samples (16 kHz).
/// 25 seconds of audio ≈ 625 encoder frames, safely under Canary 180M positional limits.
const MAX_CHUNK_SAMPLES: usize = 25 * 16_000; // 25 seconds

/// When chunking long audio, the split point is searched between this many
/// samples and `MAX_CHUNK_SAMPLES`, at the quietest spot — so chunks break in
/// a pause rather than mid-word and nothing is duplicated or lost.
const CHUNK_SPLIT_SEARCH_START: usize = 18 * 16_000; // 18 seconds

/// Energy-window size used when searching for the quietest split point.
const SPLIT_WINDOW_SAMPLES: usize = 1_600; // 100 ms

/// Find the centre of the quietest `SPLIT_WINDOW_SAMPLES` window in
/// `samples[from..to]`, used as a chunk boundary for long audio. Falls back to
/// `to` if the range is degenerate.
fn find_quietest_split(samples: &[f32], from: usize, to: usize) -> usize {
    let to = to.min(samples.len());
    if from + SPLIT_WINDOW_SAMPLES > to {
        return to;
    }

    let mut best_pos = to;
    let mut best_energy = f32::MAX;
    let mut i = from;
    while i + SPLIT_WINDOW_SAMPLES <= to {
        let energy: f32 = samples[i..i + SPLIT_WINDOW_SAMPLES]
            .iter()
            .map(|&x| x * x)
            .sum();
        if energy < best_energy {
            best_energy = energy;
            best_pos = i + SPLIT_WINDOW_SAMPLES / 2;
        }
        i += SPLIT_WINDOW_SAMPLES / 2;
    }
    best_pos
}


#[derive(Debug, Clone)]
pub struct TimestampedResult {
    pub text: String,
    pub timestamps: Vec<f32>,
    pub tokens: Vec<String>,
}

/// Language selection for the Canary 180M AED model. Canary's decoder is fed
/// a 10-token prefix where positions 4 (source) and 5 (target) are the
/// per-call language conditioning.
///
/// The original implementation set both positions to `<|unklang|>` for the
/// `Auto` variant so the model could auto-detect both source AND target
/// language from the audio. Empirically (debug builds on A059) that path
/// produced empty transcripts every time: the decoder loop hit EOS
/// immediately, leaving a zero-byte `<|endoftext|>` autoplay with no
/// generated tokens in between. Canary-180m-flash was never conditioned
/// on `(unklang_source, unksrc_target)` in its training distribution, so
/// the runtime diverges on the very first decode step.
///
/// The fix is to keep `<|unklang|>` on the **source** slot (so the audio's
/// language can still be auto-detected by the encoder) and pin the
/// **target** to `<|en|>` (English) — the dominant output in the model's
/// training mix and a deterministic, well-defined output language.
/// Trade-off: Auto mode always OUTPUTS English; users transcribing
/// Spanish/German/French whose target language is not English must pick
/// the matching explicit chip from the picker. A later iteration could
/// bolt on a runtime language ID pass to set source + target before the
/// autoregressive loop, but that requires an extra model and is out of
/// scope for this hotfix.
///
/// Mapping to vocab tokens (loaded from `canary-180m-flash-int8/vocab.txt`):
///   en     -> <|en|>      (id 62)
///   es     -> <|es|>      (id 169)
///   de     -> <|de|>      (id 76)
///   fr     -> <|fr|>      (id 69)
///   auto   -> <|unklang|> (id 21) on source, <|en|> on target
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum CanaryLanguage {
    #[default]
    Auto,
    En,
    Es,
    De,
    Fr,
}

impl CanaryLanguage {
    /// Parse a preference string. Accepts `"auto"` (default) or the ISO-639-1
    /// two-letter code. Unknown / empty values fall back to `Auto`.
    /// `transcription_language` preference from Java is a plain string
    /// (`"auto"`, `"en"`, `"es"`, `"de"`, `"fr"`); unknown values are
    /// coerced to `Auto` so a typo in the pref file does NOT crash the load.
    pub fn from_pref(s: &str) -> Self {
        match s.trim().to_ascii_lowercase().as_str() {
            "en" => CanaryLanguage::En,
            "es" => CanaryLanguage::Es,
            "de" => CanaryLanguage::De,
            "fr" => CanaryLanguage::Fr,
            // "auto" and anything else (incl. "") maps to Auto
            _ => CanaryLanguage::Auto,
        }
    }
}

#[derive(thiserror::Error, Debug)]
pub enum Parakeet180mError {
    #[error("ONNX Runtime error: {0}")]
    Ort(#[from] ort::Error),
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    #[error("Vocabulary error: {0}")]
    Vocab(String),
    #[error("Missing special token: {0}")]
    MissingToken(String),
    #[error("Model input not found: {0}")]
    InputNotFound(String),
    #[error("Failed to get tensor shape for input: {0}")]
    TensorShape(String),
}

pub struct Parakeet180mModel {
    encoder: Session,
    decoder: Session,
    vocab: Vec<String>,
    eos_token_id: i64,
    /// Per-language source/target token IDs (positions 4 and 5 of the
    /// 10-token decoder prefix). `lang_unksrc_id` is the id of
    /// `<|unklang|>` which we use as both source AND target when the user
    /// picks Auto — Canary then language-detects from the audio itself.
    lang_en_id: i64,
    lang_es_id: i64,
    lang_de_id: i64,
    lang_fr_id: i64,
    lang_unksrc_id: i64,
    /// Static prefix tokens (positions 0..=3 and 6..=9 of the 10-token
    /// decoder prefix). These are language-independent: space, then the
    /// Canary context/transcript delimiters, then the emo/PnC/ITN/timestamp/
    /// diarize toggles. Kept as plain fields so we can rebuild the prefix
    /// per call without re-tokenising.
    pre_space_id: i64,
    pre_startofcontext_id: i64,
    pre_startoftranscript_id: i64,
    pre_emo_undefined_id: i64,
    pre_pnc_id: i64,
    pre_noitn_id: i64,
    pre_notimestamp_id: i64,
    pre_nodiarize_id: i64,
    /// Current selection — defaults to Auto so a fresh install behaves the
    /// way the model was trained for (no language forcing). Update via
    /// [`Self::set_language`] from the JNI bridge when the user toggles
    /// their preference; no ONNX reload is needed because the prefix is
    /// re-tokenised at the top of every `transcribe_samples` call.
    current_lang: CanaryLanguage,
    decoder_num_layers: i64,
    decoder_hidden_size: i64,
}

impl Drop for Parakeet180mModel {
    fn drop(&mut self) {
        log::info!("Dropping Parakeet180mModel, releasing ORT sessions");
    }
}

impl Parakeet180mModel {
    pub fn from_memory(
        encoder_bytes: &[u8],
        decoder_bytes: &[u8],
        vocab_content: &str,
    ) -> Result<Self, Parakeet180mError> {
        let encoder = init_session_from_memory(encoder_bytes)?;
        let decoder = init_session_from_memory(decoder_bytes)?;

        let (vocab, token_map) = parse_vocab(vocab_content)?;

        let eos_token_id = *token_map.get("<|endoftext|>")
            .ok_or_else(|| Parakeet180mError::MissingToken("<|endoftext|>".into()))?;

        // Static (language-independent) prefix tokens. We resolve each
        // `<|...|>` ID once at load time; the language-dependent positions
        // (4 + 5) are looked up dynamically per call from `lang_*_id`.
        let pre_space_id = *token_map
            .get(" ")
            .ok_or_else(|| Parakeet180mError::MissingToken("space ' '".into()))?;
        let pre_startofcontext_id = *token_map
            .get("<|startofcontext|>")
            .ok_or_else(|| Parakeet180mError::MissingToken("<|startofcontext|>".into()))?;
        let pre_startoftranscript_id = *token_map
            .get("<|startoftranscript|>")
            .ok_or_else(|| Parakeet180mError::MissingToken("<|startoftranscript|>".into()))?;
        let pre_emo_undefined_id = *token_map
            .get("<|emo:undefined|>")
            .ok_or_else(|| Parakeet180mError::MissingToken("<|emo:undefined|>".into()))?;
        let pre_pnc_id = *token_map
            .get("<|pnc|>")
            .ok_or_else(|| Parakeet180mError::MissingToken("<|pnc|>".into()))?;
        let pre_noitn_id = *token_map
            .get("<|noitn|>")
            .ok_or_else(|| Parakeet180mError::MissingToken("<|noitn|>".into()))?;
        let pre_notimestamp_id = *token_map
            .get("<|notimestamp|>")
            .ok_or_else(|| Parakeet180mError::MissingToken("<|notimestamp|>".into()))?;
        let pre_nodiarize_id = *token_map
            .get("<|nodiarize|>")
            .ok_or_else(|| Parakeet180mError::MissingToken("<|nodiarize|>".into()))?;

        // Language tokens for source/target positions (4 + 5). Canary's
        // decoder treats these as a tag-and-generate pair: <|en|> <|es|>
        // means "transcribe English audio, output Spanish text", which is
        // how cross-lingual translation works in the Canary family. For
        // pure ASR we keep source == target from {en, es, de, fr}; for
        // the Auto chip we use <|unklang|> source + <|en|> target so the
        // encoder auto-detects the audio language while the decoder
        // emits a deterministic output (English, the dominant training
        // pair — the (unklang-source, unksrc-target) pair is out of
        // distribution on Canary-180m-flash and produces empty output).
        let lang_en_id = *token_map
            .get("<|en|>")
            .ok_or_else(|| Parakeet180mError::MissingToken("<|en|>".into()))?;
        let lang_es_id = *token_map
            .get("<|es|>")
            .ok_or_else(|| Parakeet180mError::MissingToken("<|es|>".into()))?;
        let lang_de_id = *token_map
            .get("<|de|>")
            .ok_or_else(|| Parakeet180mError::MissingToken("<|de|>".into()))?;
        let lang_fr_id = *token_map
            .get("<|fr|>")
            .ok_or_else(|| Parakeet180mError::MissingToken("<|fr|>".into()))?;
        let lang_unksrc_id = *token_map
            .get("<|unklang|>")
            .ok_or_else(|| Parakeet180mError::MissingToken("<|unklang|>".into()))?;

        let decoder_inputs = decoder.inputs();
        for input in decoder_inputs {
            log::info!(
                "180M decoder input: name={}, dtype={:?}, shape={:?}",
                input.name(),
                input.dtype(),
                input.dtype().tensor_shape()
            );
        }
        let decoder_mems_shape = decoder_inputs
            .iter()
            .find(|input| input.name() == "decoder_mems")
            .ok_or_else(|| {
                Parakeet180mError::InputNotFound("decoder_mems".to_string())
            })?
            .dtype()
            .tensor_shape()
            .ok_or_else(|| {
                Parakeet180mError::TensorShape("decoder_mems".to_string())
            })?;

        let decoder_num_layers = decoder_mems_shape[0];
        let decoder_hidden_size = decoder_mems_shape[3];

        log::info!(
            "180M decoder_mems: [{}, ?, ?, {}] (layers={}, hidden={})",
            decoder_num_layers,
            decoder_hidden_size,
            decoder_num_layers,
            decoder_hidden_size
        );

        log::info!(
            "Loaded 180M AED model: {} tokens, eos_id={}",
            vocab.len(),
            eos_token_id
        );

        Ok(Self {
            encoder,
            decoder,
            vocab,
            eos_token_id,
            lang_en_id,
            lang_es_id,
            lang_de_id,
            lang_fr_id,
            lang_unksrc_id,
            pre_space_id,
            pre_startofcontext_id,
            pre_startoftranscript_id,
            pre_emo_undefined_id,
            pre_pnc_id,
            pre_noitn_id,
            pre_notimestamp_id,
            pre_nodiarize_id,
            current_lang: CanaryLanguage::Auto,
            decoder_num_layers,
            decoder_hidden_size,
        })
    }

    /// Update the source/target language for subsequent `transcribe_samples`
    /// calls. Does NOT touch the loaded ONNX sessions — the decoder
    /// re-tokenises the prefix on every call, so only the two `lang_*_id`
    /// slots change. Safe to call from the JNI bridge thread.
    pub fn set_language(&mut self, lang: CanaryLanguage) {
        log::info!("180M set_language: {:?} -> {:?}", self.current_lang, lang);
        self.current_lang = lang;
    }

    /// Returns the language the next `transcribe_samples` call will ask
    /// the decoder to use. Useful for diagnostics / logcat smoke-testing.
    pub fn current_language(&self) -> CanaryLanguage {
        self.current_lang
    }

    /// Build the 10-token decoder prefix for `lang`. Positions 4 + 5
    /// (source/target) are derived from `lang`; all other positions are
    /// the static Canary transcript tokens (space + delimiters +
    /// emo + pnc/noitn/notimestamp/nodiarize). Delegates to the pure
    /// free function [`build_prefix_tokens`] so the prefix-building
    /// logic is unit-testable without constructing a full
    /// `Parakeet180mModel` (which requires loading ONNX sessions).
    fn build_prefix(&self, lang: CanaryLanguage) -> Vec<i64> {
        let ids = PrefixTokenIds {
            lang_en_id: self.lang_en_id,
            lang_es_id: self.lang_es_id,
            lang_de_id: self.lang_de_id,
            lang_fr_id: self.lang_fr_id,
            lang_unksrc_id: self.lang_unksrc_id,
            pre_space_id: self.pre_space_id,
            pre_startofcontext_id: self.pre_startofcontext_id,
            pre_startoftranscript_id: self.pre_startoftranscript_id,
            pre_emo_undefined_id: self.pre_emo_undefined_id,
            pre_pnc_id: self.pre_pnc_id,
            pre_noitn_id: self.pre_noitn_id,
            pre_notimestamp_id: self.pre_notimestamp_id,
            pre_nodiarize_id: self.pre_nodiarize_id,
        };
        build_prefix_tokens(lang, &ids)
    }

    pub fn transcribe_chunk(
        &mut self,
        samples: Vec<f32>,
    ) -> Result<TimestampedResult, Parakeet180mError> {
        if samples.is_empty() {
            return Ok(TimestampedResult {
                text: String::new(),
                timestamps: Vec::new(),
                tokens: Vec::new(),
            });
        }

        let features = mel_128::extract_mel_features(&samples);
        let num_frames = features.nrows();
        if num_frames == 0 {
            return Ok(TimestampedResult {
                text: String::new(),
                timestamps: Vec::new(),
                tokens: Vec::new(),
            });
        }

        // Build the 10-token decoder prefix at the TOP of
        // transcribe_samples — before any self.encoder / self.decoder
        // borrow opens. Doing it here avoids the E0502 that would fire
        // if we called self.build_prefix later (after self.encoder.run),
        // because ort's Session::run keeps a mutable borrow of
        // self.encoder tied to the lifetime of the returned outputs
        // reference. The Vec<i64> returned from build_prefix is owned
        // and does not borrow self anywhere, so it stays disjoint from
        // the decoder-loop borrows below.
        let mut input_ids = self.build_prefix(self.current_lang);
        let _prefix_len = input_ids.len();

        let features_t = features.t();
        let audio_signal_data: Vec<f32> = features_t.iter().copied().collect();
        let audio_signal = Tensor::from_array((
            vec![1i64, MEL_DIM as i64, num_frames as i64],
            audio_signal_data.into_boxed_slice(),
        ))
        .map_err(Parakeet180mError::Ort)?;

        let length = Tensor::from_array((
            vec![1i64],
            vec![num_frames as i64].into_boxed_slice(),
        ))
        .map_err(Parakeet180mError::Ort)?;

        let outputs = self
            .encoder
            .run(inputs!["audio_signal" => audio_signal, "length" => length])
            .map_err(Parakeet180mError::Ort)?;

        let enc_emb_tensor = &outputs[0];
        let (enc_emb_shape, enc_emb_data) = enc_emb_tensor
            .try_extract_tensor::<f32>()
            .map_err(Parakeet180mError::Ort)?;
        let encoder_embeddings_data: Vec<f32> = enc_emb_data.to_vec();
        let enc_t_dim = if enc_emb_shape.len() > 1 { enc_emb_shape[1] as usize } else { 0 };

        let enc_mask_tensor = &outputs[1];
        let (enc_mask_shape, enc_mask_data) = enc_mask_tensor
            .try_extract_tensor::<i64>()
            .map_err(Parakeet180mError::Ort)?;
        let encoder_mask_data: Vec<i64> = enc_mask_data.to_vec();
        let mask_t_dim = if enc_mask_shape.len() > 1 { enc_mask_shape[1] as usize } else { 0 };

        log::info!(
            "180M encoder output: embeddings=[{}, {}, {}], mask=[{}, {}], frames={}",
            enc_emb_shape.get(0).copied().unwrap_or(0),
            enc_emb_shape.get(1).copied().unwrap_or(0),
            enc_emb_shape.get(2).copied().unwrap_or(0),
            enc_mask_shape.get(0).copied().unwrap_or(0),
            enc_mask_shape.get(1).copied().unwrap_or(0),
            num_frames
        );

        if enc_t_dim == 0 || mask_t_dim == 0 {
            log::warn!(
                "180M encoder produced 0 time frames (emb={:?}, mask={:?}), returning empty result",
                enc_emb_shape,
                enc_mask_shape
            );
            return Ok(TimestampedResult {
                text: String::new(),
                timestamps: Vec::new(),
                tokens: Vec::new(),
            });
        }

        // Snapshot the language at the start of the call so a concurrent
        // `set_language` from the JNI bridge mid-transcribe does not flip
        // the prefix under our feet. The decode loop is short (a few
        // hundred steps max for typical dictation) so this is more than
        // accurate enough.
        // input_ids was already built at the top of transcribe_samples
        // before any encoder borrow opened (see the comment around
        // `let mut input_ids = self.build_prefix(...)` near features_t).
        // We just re-read the slice length here for use in the prefix
        // loop below; no re-tokenisation needed.
        let prefix_len = input_ids.len();

        // Access self.current_lang directly — CanaryLanguage is Copy
        // so reading the field does not hold a borrow on self, which is
        // important here because we are mid-function with the encoder
        // borrow (produced by self.encoder.run earlier) potentially
        // still tracked by NLL.
        log::info!(
            "180M input_ids: len={}, lang={:?}, values={:?}",
            input_ids.len(),
            self.current_lang,
            input_ids.iter().map(|x| *x as i64).collect::<Vec<_>>()
        );

        let encoder_embeddings_tensor = Tensor::from_array((
            vec![1i64, enc_t_dim as i64, enc_emb_shape[2]],
            encoder_embeddings_data.into_boxed_slice(),
        ))
        .map_err(Parakeet180mError::Ort)?;

        let encoder_mask_tensor = Tensor::from_array((
            vec![1i64, mask_t_dim as i64],
            encoder_mask_data.into_boxed_slice(),
        ))
        .map_err(Parakeet180mError::Ort)?;

        let d0: i64 = self.decoder_num_layers;
        let d3: i64 = self.decoder_hidden_size;

        let mut decoder_mems_data: Vec<f32> = vec![0.0f32; (d0 * d3) as usize];
        let mut decoder_mems_seq_len: i64 = 1;

        log::info!(
            "180M decoder: prefix={}, mems_init=[{},1,1,{}], enc_emb=[1,{},{}], enc_mask=[1,{}]",
            prefix_len,
            d0, d3, enc_t_dim, enc_emb_shape[2], mask_t_dim
        );

        for i in 0..prefix_len {
            let token = input_ids[i];
            log::info!("180M prefix step {}: token={}", i, token);
            let input_tensor = Tensor::from_array((
                vec![1i64, 1i64],
                vec![token].into_boxed_slice(),
            ))
            .map_err(Parakeet180mError::Ort)?;

            let mems_tensor = Tensor::from_array((
                vec![d0, 1i64, decoder_mems_seq_len, d3],
                decoder_mems_data.clone().into_boxed_slice(),
            ))
            .map_err(Parakeet180mError::Ort)?;

            let dec_outputs = self
                .decoder
                .run(inputs! {
                    "input_ids" => input_tensor,
                    "encoder_embeddings" => encoder_embeddings_tensor.clone(),
                    "encoder_mask" => encoder_mask_tensor.clone(),
                    "decoder_mems" => mems_tensor,
                })
                .map_err(|e| {
                    log::error!("180M decoder.run() error: {}", e);
                    Parakeet180mError::Ort(e)
                })?;

            let mems_out = &dec_outputs[1];
            let (mems_out_shape, mems_out_data) = mems_out
                .try_extract_tensor::<f32>()
                .map_err(Parakeet180mError::Ort)?;
            decoder_mems_seq_len = mems_out_shape[2];
            decoder_mems_data = mems_out_data.to_vec();
        }

        log::info!(
            "180M decoder: prefix done, generating from {} tokens, mems_seq_len={}",
            input_ids.len(),
            decoder_mems_seq_len
        );

        let mut gen_step = 0usize;
        loop {
            let last_token = input_ids.last().copied().unwrap_or(self.eos_token_id);
            if gen_step % 10 == 0 {
                log::info!("180M gen step {}: token={}", gen_step, last_token);
            }
            gen_step += 1;
            let input_tensor = Tensor::from_array((
                vec![1i64, 1i64],
                vec![last_token].into_boxed_slice(),
            ))
            .map_err(Parakeet180mError::Ort)?;

            let mems_tensor = Tensor::from_array((
                vec![d0, 1i64, decoder_mems_seq_len, d3],
                decoder_mems_data.clone().into_boxed_slice(),
            ))
            .map_err(Parakeet180mError::Ort)?;

            let dec_outputs = self
                .decoder
                .run(inputs! {
                    "input_ids" => input_tensor,
                    "encoder_embeddings" => encoder_embeddings_tensor.clone(),
                    "encoder_mask" => encoder_mask_tensor.clone(),
                    "decoder_mems" => mems_tensor,
                })
                .map_err(|e| {
                    log::error!("180M decoder.run() error: {}", e);
                    Parakeet180mError::Ort(e)
                })?;

            let logits_tensor = &dec_outputs[0];
            let (logits_shape, logits_data) = logits_tensor
                .try_extract_tensor::<f32>()
                .map_err(Parakeet180mError::Ort)?;

            let seq_len = logits_shape[1] as usize;
            let vocab_size = logits_shape[2] as usize;
            let last_step_start = (seq_len.saturating_sub(1)) * vocab_size;
            let last_step_logits = &logits_data[last_step_start..last_step_start + vocab_size];

            if last_step_logits.iter().any(|&x| x.is_nan()) {
                break;
            }

            let next_token = last_step_logits
                .iter()
                .enumerate()
                .max_by(|(_, a), (_, b)| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal))
                .map(|(idx, _)| idx as i64)
                .unwrap_or(0);

            if next_token == self.eos_token_id {
                log::info!("180M gen step {}: EOS ({})", gen_step, self.eos_token_id);
                break;
            }

            log::info!("180M gen step {}: next token={}", gen_step, next_token);
            input_ids.push(next_token);

            let mems_out = &dec_outputs[1];
            let (mems_out_shape, mems_out_data) = mems_out
                .try_extract_tensor::<f32>()
                .map_err(Parakeet180mError::Ort)?;
            decoder_mems_seq_len = mems_out_shape[2];
            decoder_mems_data = mems_out_data.to_vec();

            if input_ids.len() >= MAX_SEQUENCE_LENGTH {
                break;
            }
        }

        let token_strs: Vec<String> = input_ids[prefix_len..]
            .iter()
            .filter_map(|&id| {
                let idx = id as usize;
                if idx < self.vocab.len() {
                    let token = &self.vocab[idx];
                    if !token.starts_with("<|") && !token.is_empty() {
                        Some(token.clone())
                    } else {
                        None
                    }
                } else {
                    None
                }
            })
            .collect();

        let raw_text = token_strs.join("");
        let text = raw_text
            .replace('\u{2581}', " ")
            .split_whitespace()
            .collect::<Vec<_>>()
            .join(" ");

        Ok(TimestampedResult {
            text,
            timestamps: Vec::new(),
            tokens: token_strs,
        })
    }

    pub fn transcribe_samples(
        &mut self,
        samples: Vec<f32>,
    ) -> Result<TimestampedResult, Parakeet180mError> {
        if samples.is_empty() {
            return Ok(TimestampedResult {
                text: String::new(),
                timestamps: Vec::new(),
                tokens: Vec::new(),
            });
        }

        if samples.len() <= MAX_CHUNK_SAMPLES {
            return self.transcribe_chunk(samples);
        }

        log::info!(
            "180M: Audio has {} samples ({:.1}s), chunking into <= {:.0}s segments",
            samples.len(),
            samples.len() as f64 / 16_000.0,
            MAX_CHUNK_SAMPLES as f64 / 16_000.0,
        );

        let mut merged_text = String::new();
        let mut merged_tokens: Vec<String> = Vec::new();
        let mut merged_timestamps: Vec<f32> = Vec::new();

        let mut offset: usize = 0;
        while offset < samples.len() {
            let remaining = samples.len() - offset;
            let raw_end = if remaining <= MAX_CHUNK_SAMPLES {
                samples.len()
            } else {
                find_quietest_split(
                    &samples,
                    offset + CHUNK_SPLIT_SEARCH_START,
                    offset + MAX_CHUNK_SAMPLES,
                )
            };
            let end = raw_end.max(offset + 16_000).min(samples.len());
            let chunk_time_offset = offset as f32 / 16_000.0;

            log::info!(
                "180M: Processing chunk at {:.1}s–{:.1}s",
                chunk_time_offset,
                end as f32 / 16_000.0,
            );

            let result = self.transcribe_chunk(samples[offset..end].to_vec())?;

            let trimmed = result.text.trim();
            if !trimmed.is_empty() {
                if !merged_text.is_empty() {
                    merged_text.push(' ');
                }
                merged_text.push_str(trimmed);
                for (token, &ts) in result.tokens.iter().zip(result.timestamps.iter()) {
                    merged_tokens.push(token.clone());
                    merged_timestamps.push(ts + chunk_time_offset);
                }
            }

            offset = end;
        }

        Ok(TimestampedResult {
            text: merged_text,
            timestamps: merged_timestamps,
            tokens: merged_tokens,
        })
    }
}

// ---------------------------------------------------------------------------
// Pure prefix-building logic, extracted as a free function so it can be
// unit-tested without constructing a full Parakeet180mModel (which
// requires loading 395 MB of ONNX sessions). The struct + function are
// pub(crate) so the inline #[cfg(test)] module can access them.
// ---------------------------------------------------------------------------

/// Token IDs needed to build the Canary 180M decoder prefix. All 13 IDs
/// are resolved once at model-load time from the vocab; the prefix
/// builder is a pure function of `lang` + these IDs.
pub(crate) struct PrefixTokenIds {
    pub lang_en_id: i64,
    pub lang_es_id: i64,
    pub lang_de_id: i64,
    pub lang_fr_id: i64,
    pub lang_unksrc_id: i64,
    pub pre_space_id: i64,
    pub pre_startofcontext_id: i64,
    pub pre_startoftranscript_id: i64,
    pub pre_emo_undefined_id: i64,
    pub pre_pnc_id: i64,
    pub pre_noitn_id: i64,
    pub pre_notimestamp_id: i64,
    pub pre_nodiarize_id: i64,
}

/// Build the 10-token Canary 180M decoder prefix for `lang` using the
/// provided token IDs. Pure function — no `&self`, no ONNX dependency,
/// no side effects. Positions 4 (source) + 5 (target) are
/// language-dependent; all other positions are static Canary transcript
/// tokens (space, startofcontext, startoftranscript, emo:undefined,
/// pnc, noitn, notimestamp, nodiarize).
///
/// Auto mode: source = `<|unklang|>` (encoder auto-detects input
/// language from audio), target = `<|en|>` (deterministic English
/// output — the dominant training pair). The `(unklang, unklang)` pair
/// is out of distribution on Canary-180m-flash and produces empty
/// output, so we never set both slots to unklang.
pub(crate) fn build_prefix_tokens(
    lang: CanaryLanguage,
    ids: &PrefixTokenIds,
) -> Vec<i64> {
    let (src, tgt) = match lang {
        CanaryLanguage::Auto => (ids.lang_unksrc_id, ids.lang_en_id),
        CanaryLanguage::En => (ids.lang_en_id, ids.lang_en_id),
        CanaryLanguage::Es => (ids.lang_es_id, ids.lang_es_id),
        CanaryLanguage::De => (ids.lang_de_id, ids.lang_de_id),
        CanaryLanguage::Fr => (ids.lang_fr_id, ids.lang_fr_id),
    };
    vec![
        ids.pre_space_id,
        ids.pre_startofcontext_id,
        ids.pre_startoftranscript_id,
        ids.pre_emo_undefined_id,
        src,
        tgt,
        ids.pre_pnc_id,
        ids.pre_noitn_id,
        ids.pre_notimestamp_id,
        ids.pre_nodiarize_id,
    ]
}

fn init_session_from_memory(model_bytes: &[u8]) -> Result<Session, Parakeet180mError> {
    let mut providers = Vec::new();
    #[cfg(target_os = "android")]
    {
        providers.push(ep::NNAPI::default().build());
        providers.push(ep::XNNPACK::default().build());
    }
    providers.push(ep::CPU::default().build());

    let mut builder = Session::builder()
        .map_err(|e| Parakeet180mError::Ort(e.into()))?
        .with_optimization_level(GraphOptimizationLevel::Level3)
        .map_err(|e| Parakeet180mError::Ort(e.into()))?
        .with_execution_providers(providers)
        .map_err(|e| Parakeet180mError::Ort(e.into()))?;

    #[cfg(target_os = "android")]
    {
        builder = builder
            .with_intra_threads(4)
            .map_err(|e| Parakeet180mError::Ort(e.into()))?
            .with_inter_threads(1)
            .map_err(|e| Parakeet180mError::Ort(e.into()))?
            .with_parallel_execution(false)
            .map_err(|e| Parakeet180mError::Ort(e.into()))?;
    }
    #[cfg(not(target_os = "android"))]
    {
        builder = builder
            .with_parallel_execution(true)
            .map_err(|e| Parakeet180mError::Ort(e.into()))?;
    }

    let session = builder
        .commit_from_memory(model_bytes)
        .map_err(|e| Parakeet180mError::Ort(e.into()))?;

    Ok(session)
}

fn parse_vocab(
    content: &str,
) -> Result<(Vec<String>, HashMap<String, i64>), Parakeet180mError> {
    let mut max_id = 0usize;
    let mut tokens_with_ids: Vec<(String, usize)> = Vec::new();

    for line in content.lines() {
        let line = line.trim_end();
        if line.is_empty() {
            continue;
        }
        if let Some((token, id_str)) = line.rsplit_once(' ') {
            if let Ok(id) = id_str.parse::<usize>() {
                tokens_with_ids.push((token.to_string(), id));
                max_id = max_id.max(id);
            }
        }
    }

    let mut vocab = vec![String::new(); max_id + 1];
    let mut token_map = HashMap::new();
    for (token, id) in &tokens_with_ids {
        let clean_token = token.replace('\u{2581}', " ");
        vocab[*id] = clean_token.clone();
        token_map.insert(clean_token, *id as i64);
    }

    Ok((vocab, token_map))
}

// ---------------------------------------------------------------------------
// Unit tests for CanaryLanguage::from_pref + build_prefix_tokens.
//
// These are #[cfg(test)] — they are NOT compiled during `cargo build
// --release` (the CI Android build path), so they add zero overhead to
// the APK. They compile + run when a developer invokes `cargo test` on a
// host with the Rust toolchain installed. The tests verify:
//   1. from_pref correctly maps all 4 supported languages + Auto
//   2. from_pref is case-insensitive, trims whitespace, and falls back
//      to Auto for unknown/empty values
//   3. build_prefix_tokens produces 10 tokens for every language
//   4. Positions 4 + 5 (source/target) carry the correct language token
//   5. Auto uses (unklang_source, en_target) — NOT (unklang, unklang)
//      which produces empty output (hotfix #3 regression guard)
//   6. Static positions (0..=3, 6..=9) are language-independent
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    // --- CanaryLanguage::from_pref tests ---

    #[test]
    fn from_pref_en() {
        assert_eq!(CanaryLanguage::from_pref("en"), CanaryLanguage::En);
    }

    #[test]
    fn from_pref_es() {
        assert_eq!(CanaryLanguage::from_pref("es"), CanaryLanguage::Es);
    }

    #[test]
    fn from_pref_de() {
        assert_eq!(CanaryLanguage::from_pref("de"), CanaryLanguage::De);
    }

    #[test]
    fn from_pref_fr() {
        assert_eq!(CanaryLanguage::from_pref("fr"), CanaryLanguage::Fr);
    }

    #[test]
    fn from_pref_auto() {
        assert_eq!(CanaryLanguage::from_pref("auto"), CanaryLanguage::Auto);
    }

    #[test]
    fn from_pref_default_is_auto() {
        assert_eq!(CanaryLanguage::default(), CanaryLanguage::Auto);
    }

    #[test]
    fn from_pref_unknown_falls_back_to_auto() {
        assert_eq!(CanaryLanguage::from_pref("zh"), CanaryLanguage::Auto);
        assert_eq!(CanaryLanguage::from_pref("xyz"), CanaryLanguage::Auto);
        assert_eq!(CanaryLanguage::from_pref("english"), CanaryLanguage::Auto);
    }

    #[test]
    fn from_pref_empty_falls_back_to_auto() {
        assert_eq!(CanaryLanguage::from_pref(""), CanaryLanguage::Auto);
    }

    #[test]
    fn from_pref_case_insensitive() {
        assert_eq!(CanaryLanguage::from_pref("EN"), CanaryLanguage::En);
        assert_eq!(CanaryLanguage::from_pref("Es"), CanaryLanguage::Es);
        assert_eq!(CanaryLanguage::from_pref("DE"), CanaryLanguage::De);
        assert_eq!(CanaryLanguage::from_pref("Fr"), CanaryLanguage::Fr);
        assert_eq!(CanaryLanguage::from_pref("AUTO"), CanaryLanguage::Auto);
    }

    #[test]
    fn from_pref_trims_whitespace() {
        assert_eq!(CanaryLanguage::from_pref("  en  "), CanaryLanguage::En);
        assert_eq!(CanaryLanguage::from_pref(" auto "), CanaryLanguage::Auto);
    }

    // --- build_prefix_tokens tests ---
    // Uses the actual token IDs from canary-180m-flash-int8/vocab.txt:
    //   en=62, es=169, de=76, fr=69, unklang=21.
    // The static prefix tokens use sentinel values (1..=8) that are easy
    // to verify in assertions. The prefix builder is a pure function of
    // whatever IDs it receives, so the test is self-documenting.

    fn test_ids() -> PrefixTokenIds {
        PrefixTokenIds {
            lang_en_id: 62,
            lang_es_id: 169,
            lang_de_id: 76,
            lang_fr_id: 69,
            lang_unksrc_id: 21,
            pre_space_id: 1,
            pre_startofcontext_id: 2,
            pre_startoftranscript_id: 3,
            pre_emo_undefined_id: 4,
            pre_pnc_id: 5,
            pre_noitn_id: 6,
            pre_notimestamp_id: 7,
            pre_nodiarize_id: 8,
        }
    }

    #[test]
    fn build_prefix_always_returns_10_tokens() {
        let ids = test_ids();
        for lang in [
            CanaryLanguage::Auto,
            CanaryLanguage::En,
            CanaryLanguage::Es,
            CanaryLanguage::De,
            CanaryLanguage::Fr,
        ] {
            let prefix = build_prefix_tokens(lang, &ids);
            assert_eq!(
                prefix.len(),
                10,
                "prefix for {:?} should be 10 tokens, got {}",
                lang,
                prefix.len()
            );
        }
    }

    #[test]
    fn build_prefix_en_uses_en_on_both_positions() {
        let ids = test_ids();
        let prefix = build_prefix_tokens(CanaryLanguage::En, &ids);
        assert_eq!(prefix[4], 62, "source (pos 4) should be en_id=62");
        assert_eq!(prefix[5], 62, "target (pos 5) should be en_id=62");
    }

    #[test]
    fn build_prefix_es_uses_es_on_both_positions() {
        let ids = test_ids();
        let prefix = build_prefix_tokens(CanaryLanguage::Es, &ids);
        assert_eq!(prefix[4], 169, "source (pos 4) should be es_id=169");
        assert_eq!(prefix[5], 169, "target (pos 5) should be es_id=169");
    }

    #[test]
    fn build_prefix_de_uses_de_on_both_positions() {
        let ids = test_ids();
        let prefix = build_prefix_tokens(CanaryLanguage::De, &ids);
        assert_eq!(prefix[4], 76, "source (pos 4) should be de_id=76");
        assert_eq!(prefix[5], 76, "target (pos 5) should be de_id=76");
    }

    #[test]
    fn build_prefix_fr_uses_fr_on_both_positions() {
        let ids = test_ids();
        let prefix = build_prefix_tokens(CanaryLanguage::Fr, &ids);
        assert_eq!(prefix[4], 69, "source (pos 4) should be fr_id=69");
        assert_eq!(prefix[5], 69, "target (pos 5) should be fr_id=69");
    }

    #[test]
    fn build_prefix_auto_uses_unklang_source_en_target() {
        // This is the hotfix #3 fix: Auto maps to (unklang_src, en_target)
        // — NOT (unklang_src, unklang_target) which produces empty output
        // because Canary-180m-flash was never trained on that pair.
        let ids = test_ids();
        let prefix = build_prefix_tokens(CanaryLanguage::Auto, &ids);
        assert_eq!(
            prefix[4], 21,
            "source (pos 4) should be unklang_id=21 for auto-detect"
        );
        assert_eq!(
            prefix[5], 62,
            "target (pos 5) should be en_id=62 (deterministic English output)"
        );
    }

    #[test]
    fn build_prefix_auto_never_uses_unklang_on_target() {
        // Regression guard for hotfix #3: the (unklang, unklang) pair is
        // out of distribution on Canary-180m-flash and produces empty
        // output (decoder converges to EOS at step 1). This test will
        // FAIL if someone reverts the fix by setting both slots to
        // unklang — catching the regression before it ships.
        let ids = test_ids();
        let prefix = build_prefix_tokens(CanaryLanguage::Auto, &ids);
        assert_ne!(
            prefix[5], 21,
            "target must NOT be unklang — (unklang, unklang) produces empty output"
        );
    }

    #[test]
    fn build_prefix_static_positions_are_language_independent() {
        // Positions 0..=3 and 6..=9 are identical regardless of language.
        let ids = test_ids();
        let en_prefix = build_prefix_tokens(CanaryLanguage::En, &ids);
        let es_prefix = build_prefix_tokens(CanaryLanguage::Es, &ids);
        let auto_prefix = build_prefix_tokens(CanaryLanguage::Auto, &ids);

        for i in [0usize, 1, 2, 3, 6, 7, 8, 9] {
            assert_eq!(
                en_prefix[i], es_prefix[i],
                "static pos {} differs between en and es", i
            );
            assert_eq!(
                en_prefix[i], auto_prefix[i],
                "static pos {} differs between en and auto", i
            );
        }
        // Verify the actual static values match the sentinel IDs.
        assert_eq!(en_prefix[0], 1, "pos 0 = space");
        assert_eq!(en_prefix[1], 2, "pos 1 = startofcontext");
        assert_eq!(en_prefix[2], 3, "pos 2 = startoftranscript");
        assert_eq!(en_prefix[3], 4, "pos 3 = emo:undefined");
        assert_eq!(en_prefix[6], 5, "pos 6 = pnc");
        assert_eq!(en_prefix[7], 6, "pos 7 = noitn");
        assert_eq!(en_prefix[8], 7, "pos 8 = notimestamp");
        assert_eq!(en_prefix[9], 8, "pos 9 = nodiarize");
    }

    #[test]
    fn build_prefix_auto_source_differs_from_en_source() {
        // Auto uses unklang on source; En uses en on source. These must
        // differ — if they were the same, Auto would be identical to En
        // and the encoder would lose its auto-detect capability.
        let ids = test_ids();
        let auto_prefix = build_prefix_tokens(CanaryLanguage::Auto, &ids);
        let en_prefix = build_prefix_tokens(CanaryLanguage::En, &ids);
        assert_ne!(
            auto_prefix[4], en_prefix[4],
            "Auto source (unklang) must differ from En source (en) — \
             otherwise auto-detect is lost"
        );
    }

    #[test]
    fn test_find_quietest_split_detects_silence() {
        let mut samples = vec![1.0f32; 100_000];
        // Insert a 1600-sample silent region around index 50,000
        for i in 50_000..51_600 {
            samples[i] = 0.0;
        }
        let split = find_quietest_split(&samples, 10_000, 90_000);
        // Best position should be inside the silent window [50_000, 51_600]
        assert!(
            split >= 50_000 && split <= 51_600,
            "expected split point inside silent window [50000, 51600], got {}",
            split
        );
    }
}
