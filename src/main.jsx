import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

const SAMPLE_TEXT = `Speed reading is not about bullying your eyes into panic. It is about removing drag. A good reader builds rhythm, trusts focus, and lets each phrase arrive cleanly. This little machine is designed to help with that. Start slow, find the pace where the words still feel meaningful, then increase speed in small steps. The goal is not to win a number. The goal is to read with momentum.`;

const STORAGE_KEY = 'speed-reader-3d-state-v1';

const DEFAULT_STATE = {
  text: SAMPLE_TEXT,
  wpm: 320,
  chunkSize: 1,
  punctuationBoost: true,
  focusMode: 'tunnel',
  autoStart: false,
};

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, Number(value) || min));
}

function formatTime(ms) {
  if (!Number.isFinite(ms) || ms <= 0) return '0:00';
  const seconds = Math.ceil(ms / 1000);
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;
  return `${minutes}:${String(remainder).padStart(2, '0')}`;
}

function normalizeText(text) {
  return text.replace(/\s+/g, ' ').trim();
}

function splitIntoChunks(text, chunkSize) {
  const words = normalizeText(text).match(/\S+/g) || [];
  const chunks = [];

  for (let index = 0; index < words.length; index += chunkSize) {
    const group = words.slice(index, index + chunkSize);
    const lastWord = group[group.length - 1] || '';
    const sentenceEnd = /[.!?][\]})"'”’]*$/.test(lastWord);
    const softPause = /[,;:][\]})"'”’]*$/.test(lastWord);

    chunks.push({
      text: group.join(' '),
      wordCount: group.length,
      startWord: index + 1,
      endWord: index + group.length,
      pauseWeight: sentenceEnd ? 1.85 : softPause ? 1.32 : 1,
    });
  }

  return chunks;
}

function getChunkDelay(chunk, wpm, punctuationBoost) {
  const baseWordDelay = 60000 / clamp(wpm, 80, 1500);
  const boost = punctuationBoost ? chunk.pauseWeight : 1;
  return Math.max(90, baseWordDelay * chunk.wordCount * boost);
}

function usePersistentState() {
  const [state, setState] = useState(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY));
      return { ...DEFAULT_STATE, ...saved };
    } catch {
      return DEFAULT_STATE;
    }
  });

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }, [state]);

  return [state, setState];
}

function StatCard({ label, value }) {
  return (
    <div className="stat-card">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function ControlButton({ children, active = false, ...props }) {
  return (
    <button className={active ? 'button active' : 'button'} type="button" {...props}>
      {children}
    </button>
  );
}

function App() {
  const [settings, setSettings] = usePersistentState();
  const [activeIndex, setActiveIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(settings.autoStart);
  const timerRef = useRef(null);

  const chunks = useMemo(
    () => splitIntoChunks(settings.text, settings.chunkSize),
    [settings.text, settings.chunkSize]
  );

  const totalWords = chunks.reduce((sum, chunk) => sum + chunk.wordCount, 0);
  const activeChunk = chunks[activeIndex] || null;
  const previousChunk = chunks[activeIndex - 1] || null;
  const nextChunk = chunks[activeIndex + 1] || null;
  const progress = chunks.length <= 1 ? 0 : (activeIndex / Math.max(1, chunks.length - 1)) * 100;
  const wordsRead = activeChunk ? activeChunk.endWord : 0;
  const wordsRemaining = Math.max(0, totalWords - wordsRead);
  const estimatedRemainingMs = (wordsRemaining / clamp(settings.wpm, 80, 1500)) * 60000;
  const totalEstimatedMs = (totalWords / clamp(settings.wpm, 80, 1500)) * 60000;

  useEffect(() => {
    if (activeIndex >= chunks.length && chunks.length > 0) {
      setActiveIndex(Math.max(0, chunks.length - 1));
    }
  }, [activeIndex, chunks.length]);

  useEffect(() => {
    clearTimeout(timerRef.current);

    if (!isPlaying || !activeChunk) return;

    timerRef.current = setTimeout(() => {
      setActiveIndex((current) => {
        if (current >= chunks.length - 1) {
          setIsPlaying(false);
          return current;
        }
        return current + 1;
      });
    }, getChunkDelay(activeChunk, settings.wpm, settings.punctuationBoost));

    return () => clearTimeout(timerRef.current);
  }, [isPlaying, activeChunk, chunks.length, settings.wpm, settings.punctuationBoost]);

  useEffect(() => {
    const onKeyDown = (event) => {
      const tagName = document.activeElement?.tagName;
      const isTyping = tagName === 'TEXTAREA' || tagName === 'INPUT' || document.activeElement?.isContentEditable;
      if (isTyping) return;

      if (event.code === 'Space') {
        event.preventDefault();
        setIsPlaying((value) => !value);
      }

      if (event.key === 'ArrowRight') {
        setIsPlaying(false);
        setActiveIndex((index) => Math.min(chunks.length - 1, index + 1));
      }

      if (event.key === 'ArrowLeft') {
        setIsPlaying(false);
        setActiveIndex((index) => Math.max(0, index - 1));
      }

      if (event.key.toLowerCase() === 'r') {
        setIsPlaying(false);
        setActiveIndex(0);
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [chunks.length]);

  function updateSetting(key, value) {
    setSettings((current) => ({ ...current, [key]: value }));
  }

  function handleTextChange(value) {
    updateSetting('text', value);
    setActiveIndex(0);
    setIsPlaying(false);
  }

  function jumpBy(amount) {
    setIsPlaying(false);
    setActiveIndex((index) => clamp(index + amount, 0, Math.max(0, chunks.length - 1)));
  }

  function resetSession() {
    setIsPlaying(false);
    setActiveIndex(0);
  }

  return (
    <main className={`app focus-${settings.focusMode}`}>
      <section className="hero-panel">
        <div className="hero-copy">
          <p className="eyebrow">Speed Reader 3D</p>
          <h1>Read inside a focus tunnel.</h1>
          <p className="hero-text">
            Paste text, pick your pace, and let each word land in the center of the cockpit.
            It is fast reading without the frantic slot-machine feeling.
          </p>
        </div>

        <div className="stats-grid" aria-label="Reading statistics">
          <StatCard label="Words" value={totalWords.toLocaleString()} />
          <StatCard label="WPM" value={settings.wpm} />
          <StatCard label="Remaining" value={formatTime(estimatedRemainingMs)} />
          <StatCard label="Session" value={formatTime(totalEstimatedMs)} />
        </div>
      </section>

      <section className="reader-shell" aria-label="3D speed reader">
        <div className="reader-stage">
          <div className="tunnel" aria-hidden="true">
            <span className="ring ring-1" />
            <span className="ring ring-2" />
            <span className="ring ring-3" />
            <span className="ring ring-4" />
            <span className="horizon" />
          </div>

          <div className="reader-card">
            <div className="reader-meta">
              <span>{activeChunk ? `${activeChunk.startWord}-${activeChunk.endWord}` : '0'}</span>
              <span>{Math.round(progress)}%</span>
            </div>

            <div className="ghost-word previous" aria-hidden="true">
              {previousChunk?.text || 'Ready'}
            </div>

            <div className="focus-word" aria-live="polite">
              {activeChunk?.text || 'Paste text to begin'}
            </div>

            <div className="ghost-word next" aria-hidden="true">
              {nextChunk?.text || 'Complete'}
            </div>

            <div className="progress-track" aria-label="Reading progress">
              <span style={{ width: `${progress}%` }} />
            </div>
          </div>
        </div>

        <div className="transport">
          <ControlButton onClick={() => jumpBy(-1)}>Back</ControlButton>
          <ControlButton active={isPlaying} onClick={() => setIsPlaying((value) => !value)}>
            {isPlaying ? 'Pause' : 'Play'}
          </ControlButton>
          <ControlButton onClick={() => jumpBy(1)}>Next</ControlButton>
          <ControlButton onClick={resetSession}>Reset</ControlButton>
        </div>
      </section>

      <section className="workbench">
        <div className="editor-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Input</p>
              <h2>Your text</h2>
            </div>
            <div className="inline-actions">
              <button type="button" onClick={() => handleTextChange(SAMPLE_TEXT)}>Use sample</button>
              <button type="button" onClick={() => handleTextChange('')}>Clear</button>
            </div>
          </div>

          <textarea
            value={settings.text}
            onChange={(event) => handleTextChange(event.target.value)}
            placeholder="Paste something here and press Play."
            spellCheck="false"
          />
        </div>

        <aside className="settings-card" aria-label="Reading settings">
          <div className="section-heading compact">
            <div>
              <p className="eyebrow">Controls</p>
              <h2>Reading engine</h2>
            </div>
          </div>

          <label className="range-control">
            <span>Speed: <strong>{settings.wpm} WPM</strong></span>
            <input
              type="range"
              min="100"
              max="900"
              step="10"
              value={settings.wpm}
              onChange={(event) => updateSetting('wpm', clamp(event.target.value, 100, 900))}
            />
          </label>

          <label className="range-control">
            <span>Chunk size: <strong>{settings.chunkSize} word{settings.chunkSize > 1 ? 's' : ''}</strong></span>
            <input
              type="range"
              min="1"
              max="4"
              step="1"
              value={settings.chunkSize}
              onChange={(event) => {
                updateSetting('chunkSize', clamp(event.target.value, 1, 4));
                setActiveIndex(0);
                setIsPlaying(false);
              }}
            />
          </label>

          <div className="toggle-row">
            <span>
              <strong>Punctuation pauses</strong>
              <small>Gives sentences a tiny breath.</small>
            </span>
            <button
              className={settings.punctuationBoost ? 'switch on' : 'switch'}
              type="button"
              aria-pressed={settings.punctuationBoost}
              onClick={() => updateSetting('punctuationBoost', !settings.punctuationBoost)}
            >
              <span />
            </button>
          </div>

          <div className="mode-picker">
            <span>Visual mode</span>
            <div>
              {['tunnel', 'glass', 'plain'].map((mode) => (
                <button
                  key={mode}
                  className={settings.focusMode === mode ? 'selected' : ''}
                  type="button"
                  onClick={() => updateSetting('focusMode', mode)}
                >
                  {mode}
                </button>
              ))}
            </div>
          </div>

          <div className="keyboard-card">
            <strong>Keyboard</strong>
            <p>Space plays or pauses. Arrows move. R resets.</p>
          </div>
        </aside>
      </section>
    </main>
  );
}

createRoot(document.getElementById('root')).render(<App />);
