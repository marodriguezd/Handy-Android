use log::info;
use rusqlite::{params, Connection};
use serde::{Deserialize, Serialize};
use std::path::Path;
use std::sync::Mutex;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct HistoryEntry {
    pub id: i64,
    pub text: String,
    #[serde(rename = "post_processed_text")]
    pub post_processed: Option<String>,
    pub timestamp: i64,
    #[serde(rename = "is_saved")]
    pub saved: bool,
    #[serde(rename = "audio_path")]
    pub wav_path: Option<String>,
}

#[derive(Debug, Clone)]
pub enum HistoryError {
    DatabaseError(String),
    EntryNotFound(i64),
    IoError(String),
}

impl std::fmt::Display for HistoryError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            HistoryError::DatabaseError(s) => write!(f, "Database error: {s}"),
            HistoryError::EntryNotFound(id) => write!(f, "Entry {id} not found"),
            HistoryError::IoError(s) => write!(f, "I/O error: {s}"),
        }
    }
}

impl std::error::Error for HistoryError {}

impl From<rusqlite::Error> for HistoryError {
    fn from(e: rusqlite::Error) -> Self {
        HistoryError::DatabaseError(e.to_string())
    }
}

pub struct HistoryManager {
    db: Mutex<Connection>,
}

impl HistoryManager {
    pub fn new(db_path: &str) -> Result<Self, HistoryError> {
        if let Some(parent) = Path::new(db_path).parent() {
            std::fs::create_dir_all(parent)
                .map_err(|e| HistoryError::IoError(format!("Failed to create db directory: {e}")))?;
        }

        let conn = Connection::open(db_path)
            .map_err(|e| HistoryError::IoError(format!("Failed to open database: {e}")))?;

        conn.execute_batch(
            "CREATE TABLE IF NOT EXISTS history (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                text        TEXT NOT NULL,
                post_processed TEXT,
                wav_path    TEXT,
                timestamp   INTEGER NOT NULL,
                saved       INTEGER NOT NULL DEFAULT 0
            );
            CREATE INDEX IF NOT EXISTS idx_history_timestamp
                ON history(timestamp DESC);",
        )
        .map_err(|e| HistoryError::DatabaseError(format!("Failed to create schema: {e}")))?;

        info!("History database initialized at {db_path}");
        Ok(Self {
            db: Mutex::new(conn),
        })
    }

    pub fn save_entry(
        &self,
        text: &str,
        post_processed: Option<&str>,
        wav_path: Option<&str>,
    ) -> Result<i64, HistoryError> {
        let db = self.db.lock().unwrap();
        let timestamp = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as i64;

        db.execute(
            "INSERT INTO history (text, post_processed, wav_path, timestamp) VALUES (?1, ?2, ?3, ?4)",
            params![text, post_processed, wav_path, timestamp],
        )?;

        let id = db.last_insert_rowid();
        info!("Saved history entry {id}");
        Ok(id)
    }

    pub fn get_entries(&self, offset: i64, limit: i64) -> Result<Vec<HistoryEntry>, HistoryError> {
        let db = self.db.lock().unwrap();
        let mut stmt = db
            .prepare(
                "SELECT id, text, post_processed, wav_path, timestamp, saved
                 FROM history
                 ORDER BY timestamp DESC
                 LIMIT ?1 OFFSET ?2",
            )
            .map_err(|e| HistoryError::DatabaseError(e.to_string()))?;

        let entries = stmt
            .query_map(params![limit, offset], |row| {
                let saved_int: i32 = row.get(5)?;
                Ok(HistoryEntry {
                    id: row.get(0)?,
                    text: row.get(1)?,
                    post_processed: row.get(2)?,
                    wav_path: row.get(3)?,
                    timestamp: row.get(4)?,
                    saved: saved_int != 0,
                })
            })
            .map_err(|e| HistoryError::DatabaseError(e.to_string()))?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|e| HistoryError::DatabaseError(e.to_string()))?;

        Ok(entries)
    }

    pub fn delete_entry(&self, id: i64) -> Result<(), HistoryError> {
        let db = self.db.lock().unwrap();
        let affected = db
            .execute("DELETE FROM history WHERE id = ?1", params![id])
            .map_err(|e| HistoryError::DatabaseError(e.to_string()))?;
        if affected == 0 {
            return Err(HistoryError::EntryNotFound(id));
        }
        info!("Deleted history entry {id}");
        Ok(())
    }

    pub fn toggle_saved(&self, id: i64) -> Result<(), HistoryError> {
        let db = self.db.lock().unwrap();
        let affected = db
            .execute(
                "UPDATE history SET saved = CASE WHEN saved = 0 THEN 1 ELSE 0 END WHERE id = ?1",
                params![id],
            )
            .map_err(|e| HistoryError::DatabaseError(e.to_string()))?;
        if affected == 0 {
            return Err(HistoryError::EntryNotFound(id));
        }
        info!("Toggled saved for history entry {id}");
        Ok(())
    }

    pub fn new_empty() -> Self {
        Self {
            db: Mutex::new(Connection::open_in_memory().expect("Failed to create in-memory history database")),
        }
    }
}
