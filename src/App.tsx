import React, { useState } from 'react';
import {
  Shield,
  Layers,
  FolderLock,
  Search,
  Cpu,
  Cloud,
  CheckCircle2,
  GitBranch,
  FileCode2,
  Lock,
  Terminal,
  Zap,
  BookOpen,
  ArrowRight,
  Database,
  ExternalLink,
  Sparkles,
  Award,
  RefreshCw,
  Copy,
  Check
} from 'lucide-react';

interface PhaseItem {
  id: number;
  title: string;
  category: 'Foundation' | 'Core Engine' | 'AI & Plugins' | 'Cloud & System' | 'Quality & Release';
  status: 'In Progress' | 'Pending' | 'Completed';
  description: string;
  keyDeliverables: string[];
}

const PHASES: PhaseItem[] = [
  {
    id: 1,
    title: 'Phase 1 — Technical Research [No Code]',
    category: 'Foundation',
    status: 'In Progress',
    description: 'Deep-dive technical investigation across frozen Android stack: Keystore, SQLCipher, WorkManager, TFLite embeddings, and SPI plugin isolation.',
    keyDeliverables: ['Security Architecture Verification', 'Offline-First Data Flow Models', 'ML Kit Dynamic Feature Delivery Spec', 'Cold-Start Latency Baseline Plan']
  },
  {
    id: 2,
    title: 'Phase 2 — Architecture Freeze',
    category: 'Foundation',
    status: 'Pending',
    description: 'Finalize multi-module Gradle topology, Dependency Injection graph with Hilt, and Plugin SPI interface contracts.',
    keyDeliverables: ['Module Dependency Graph', 'Hilt DI Scopes & Component Boundaries', 'Plugin SPI Interfaces Freeze']
  },
  {
    id: 3,
    title: 'Phase 3 — Project Foundation',
    category: 'Foundation',
    status: 'Pending',
    description: 'Bootstrap Kotlin DSL Gradle build scripts, Version Catalog (libs.versions.toml), ProGuard/R8 rules, and Base Theme.',
    keyDeliverables: ['Multi-module Gradle Setup', 'Material 3 Color Scheme & Typography', 'CI/CD Pipeline with GitHub Actions']
  },
  {
    id: 4,
    title: 'Phase 4 — Database & Security',
    category: 'Core Engine',
    status: 'Pending',
    description: 'Implement SQLCipher-encrypted Room database with Keystore key wrapping, DAO patterns, and secure journal logging.',
    keyDeliverables: ['Room + SQLCipher 256-bit AES', 'Android Keystore Key Hierarchy', 'FTS4/FTS5 Search Tables']
  },
  {
    id: 5,
    title: 'Phase 5 — Core File Manager',
    category: 'Core Engine',
    status: 'Pending',
    description: 'High-speed storage indexer, SAF integration, file operations, and Duplicate Cleaner (Level 1-2).',
    keyDeliverables: ['SAF & Storage Manager', 'Hash-based Duplicate Detection', 'File Metadata Repository']
  },
  {
    id: 6,
    title: 'Phase 6 — Secure Vault',
    category: 'Core Engine',
    status: 'Pending',
    description: 'Hardware-backed encrypted vault container with zero-knowledge PIN, biometric auth, and decoy vault support.',
    keyDeliverables: ['Encrypted File Chunking', 'BiometricPrompt Integration', 'Auto-Lock & Tamper Detection']
  },
  {
    id: 7,
    title: 'Phase 7 — Search Engine (Core)',
    category: 'Core Engine',
    status: 'Pending',
    description: 'Offline FTS full-text search engine indexing file names, mime-types, tags, and cached text metadata.',
    keyDeliverables: ['Room FTS Query Optimizer', 'Fast Tokenizer & Prefix Search', 'Filter Faceting System']
  },
  {
    id: 8,
    title: 'Phase 8 — OCR Engine (Plugin)',
    category: 'AI & Plugins',
    status: 'Pending',
    description: 'Downloadable on-demand plugin utilizing Google ML Kit Text Recognition for offline document parsing.',
    keyDeliverables: ['Dynamic Feature Module Delivery', 'Batch OCR Background Worker', 'Bounding Box & Text Extractor']
  },
  {
    id: 9,
    title: 'Phase 9 — AI Semantic Search (Plugin)',
    category: 'AI & Plugins',
    status: 'Pending',
    description: 'On-device TFLite lightweight embedding model generating local semantic vector embeddings for natural language search.',
    keyDeliverables: ['TFLite Vector Encoder (<15MB)', 'Local Cosine Similarity Matcher', 'Zero Network Telemetry']
  },
  {
    id: 10,
    title: 'Phase 10 — AI Intelligence',
    category: 'AI & Plugins',
    status: 'Pending',
    description: 'Similarity threshold slider (70%-95%), smart file categorization, and intelligent clustering.',
    keyDeliverables: ['Dynamic Cosine Slider UI', 'Automatic Topic Clustering', 'Smart Deduplication Suggestions']
  },
  {
    id: 11,
    title: 'Phase 11 — Cloud Core & Plugins',
    category: 'Cloud & System',
    status: 'Pending',
    description: 'Google Drive Core integration via Credential Manager + REST API; OneDrive, Dropbox, S3, NextCloud as SPI plugins.',
    keyDeliverables: ['Google Drive REST API Service', 'OAuth2 Credential Manager', 'Cloud SPI Driver Registry']
  },
  {
    id: 12,
    title: 'Phase 12 — Background System',
    category: 'Cloud & System',
    status: 'Pending',
    description: 'WorkManager scheduled workers for file indexing, backup sync, junk cleaning, and battery-friendly constraints.',
    keyDeliverables: ['Periodic Indexing Worker', 'Battery & Network Constraints', 'Foreground Progress Notifications']
  },
  {
    id: 13,
    title: 'Phase 13 — UI & UX Craft',
    category: 'Cloud & System',
    status: 'Pending',
    description: 'Polished Jetpack Compose screens matching brand palette (#F47B20, #102B52), micro-interactions, and accessibility.',
    keyDeliverables: ['Jetpack Compose Material 3 UI', 'Dark & Light Adaptive Themes', 'Custom Golden Leaf Animations']
  },
  {
    id: 14,
    title: 'Phase 14 — Plugin System Architecture',
    category: 'AI & Plugins',
    status: 'Pending',
    description: 'Safe dynamic classloader sandbox, plugin lifecycle manager, and permission isolation.',
    keyDeliverables: ['Plugin SPI Lifecycle Bridge', 'Security Sandbox Verification', 'Dynamic Module Installer']
  },
  {
    id: 15,
    title: 'Phase 15 — Optimization (<10s Cold Start)',
    category: 'Quality & Release',
    status: 'Pending',
    description: 'R8 full optimization, Baseline Profiles, App Startup initializer, memory leak audits with LeakCanary.',
    keyDeliverables: ['Baseline Profile Generation', 'App Startup Lazy Initialization', 'Cold Start < 3.2s on Mid-range']
  },
  {
    id: 16,
    title: 'Phase 16 — Comprehensive Testing',
    category: 'Quality & Release',
    status: 'Pending',
    description: 'Robolectric JVM unit tests, CUJ integration flows, crypto integrity benchmarks, and security validation.',
    keyDeliverables: ['95%+ Domain & Repo Test Coverage', 'Robolectric Screenshot Tests', 'Crypto Stress & Fuzz Tests']
  },
  {
    id: 17,
    title: 'Phase 17 — Documentation',
    category: 'Quality & Release',
    status: 'Pending',
    description: 'Security Whitepaper, Architecture Specification, Plugin Developer Guide, and User Manual.',
    keyDeliverables: ['Security Whitepaper v2.0', 'Plugin SDK Documentation', 'Play Store Metadata & Policies']
  },
  {
    id: 18,
    title: 'Phase 18 — Production Audit',
    category: 'Quality & Release',
    status: 'Pending',
    description: 'Zero-vulnerability OWASP Mobile Top 10 compliance audit, APK analyzer size reduction, key signing setup.',
    keyDeliverables: ['OWASP MASVS Audit Report', 'Release Keystore Hardening', 'APK Size < 18MB Base']
  },
  {
    id: 19,
    title: 'Phase 19 — Final Release',
    category: 'Quality & Release',
    status: 'Pending',
    description: 'AAB build generation, Play Console release tracks, GitHub Release tagging, and production verification.',
    keyDeliverables: ['Signed Release AAB/APK', 'GitHub Release v1.0.0 Tag', 'Production Release Verification']
  }
];

export default function App() {
  const [activeTab, setActiveTab] = useState<'overview' | 'phases' | 'research' | 'github'>('overview');
  const [copiedCmd, setCopiedCmd] = useState<string | null>(null);

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedCmd(id);
    setTimeout(() => setCopiedCmd(null), 2000);
  };

  return (
    <div className="min-h-screen bg-[#0d1b2e] text-slate-100 flex flex-col font-sans selection:bg-[#F47B20]/30 selection:text-white">
      {/* Top Header */}
      <header className="border-b border-slate-800/80 bg-[#102B52]/90 backdrop-blur sticky top-0 z-40 px-6 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            {/* Golden Leaf Logo */}
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-[#D4A95A] to-[#F47B20] p-0.5 shadow-lg shadow-[#F47B20]/20 flex items-center justify-center">
              <div className="w-full h-full bg-[#102B52] rounded-[10px] flex items-center justify-center">
                <span className="font-black text-xs text-[#D4A95A] tracking-wider">VVF</span>
              </div>
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-lg font-bold tracking-tight text-white">VVF Smart Manager</h1>
                <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-[#F47B20]/20 text-[#F47B20] border border-[#F47B20]/30">
                  Phase 1 Active
                </span>
              </div>
              <p className="text-xs text-slate-400">Senior Android Engineering & AI Coding Workspace</p>
            </div>
          </div>

          {/* Navigation tabs */}
          <nav className="flex items-center gap-1 bg-[#0b1728] p-1 rounded-xl border border-slate-800">
            <button
              id="tab-overview"
              onClick={() => setActiveTab('overview')}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                activeTab === 'overview'
                  ? 'bg-[#F47B20] text-white shadow'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
              }`}
            >
              Overview
            </button>
            <button
              id="tab-phases"
              onClick={() => setActiveTab('phases')}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                activeTab === 'phases'
                  ? 'bg-[#F47B20] text-white shadow'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
              }`}
            >
              19-Phase Roadmap
            </button>
            <button
              id="tab-research"
              onClick={() => setActiveTab('research')}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                activeTab === 'research'
                  ? 'bg-[#F47B20] text-white shadow'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
              }`}
            >
              Phase 1 Research
            </button>
            <button
              id="tab-github"
              onClick={() => setActiveTab('github')}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all flex items-center gap-1.5 ${
                activeTab === 'github'
                  ? 'bg-[#F47B20] text-white shadow'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
              }`}
            >
              <GitBranch className="w-3.5 h-3.5" />
              GitHub Sync
            </button>
          </nav>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-6 py-8 flex-1 w-full space-y-8">
        {/* Golden Rule Banner */}
        <div className="bg-gradient-to-r from-[#102B52] via-[#0f2442] to-[#102B52] border border-[#F47B20]/30 rounded-2xl p-5 shadow-xl relative overflow-hidden">
          <div className="absolute -right-6 -bottom-6 w-32 h-32 bg-[#F47B20]/10 rounded-full blur-2xl pointer-events-none" />
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <Award className="w-5 h-5 text-[#D4A95A]" />
                <h2 className="text-sm font-bold tracking-wide uppercase text-[#D4A95A]">
                  Golden Engineering Rule
                </h2>
              </div>
              <p className="text-xs text-slate-300 max-w-2xl">
                Research → Verify → Plan → Approval → Develop → Test → Review → Document → Next Phase
              </p>
              <p className="text-[11px] text-slate-400 italic">
                (कोई भी चरण Skip नहीं होगा। कोई चरण Fail होने पर अगले चरण पर नहीं जाएँगे।)
              </p>
            </div>

            <div className="flex items-center gap-2 bg-[#0b1728]/80 px-4 py-2 rounded-xl border border-slate-800">
              <span className="w-2 h-2 rounded-full bg-[#3FA34D] animate-pulse" />
              <span className="text-xs font-semibold text-slate-300">
                Current Stage: <span className="text-[#5BC0EB]">Phase 1 Technical Research</span>
              </span>
            </div>
          </div>
        </div>

        {/* Tab 1: Overview */}
        {activeTab === 'overview' && (
          <div className="space-y-8 animate-fadeIn">
            {/* Frozen Tech Stack Grid */}
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-base font-bold text-white flex items-center gap-2">
                    <Layers className="w-4 h-4 text-[#5BC0EB]" />
                    Strictly Frozen Technology Stack
                  </h3>
                  <p className="text-xs text-slate-400">Fixed architectural baseline for VVF Smart Manager</p>
                </div>
                <span className="text-xs font-mono bg-slate-800 text-slate-300 px-2.5 py-1 rounded-md border border-slate-700">
                  pkg: com.vvf.smartmanager
                </span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-4 gap-4">
                <div className="bg-[#102B52]/50 border border-slate-800/80 rounded-xl p-4 space-y-2 hover:border-[#F47B20]/40 transition-colors">
                  <div className="flex items-center gap-2 text-xs font-semibold text-[#D4A95A]">
                    <FileCode2 className="w-4 h-4" /> Language & UI
                  </div>
                  <p className="text-sm font-bold text-white">Kotlin + Jetpack Compose</p>
                  <p className="text-xs text-slate-400">Material 3, Navigation Component, Coroutines & Flow</p>
                </div>

                <div className="bg-[#102B52]/50 border border-slate-800/80 rounded-xl p-4 space-y-2 hover:border-[#F47B20]/40 transition-colors">
                  <div className="flex items-center gap-2 text-xs font-semibold text-[#5BC0EB]">
                    <Database className="w-4 h-4" /> Data & Encryption
                  </div>
                  <p className="text-sm font-bold text-white">Room + SQLCipher</p>
                  <p className="text-xs text-slate-400">256-bit AES DB encryption + FTS4 full-text search</p>
                </div>

                <div className="bg-[#102B52]/50 border border-slate-800/80 rounded-xl p-4 space-y-2 hover:border-[#F47B20]/40 transition-colors">
                  <div className="flex items-center gap-2 text-xs font-semibold text-[#3FA34D]">
                    <Shield className="w-4 h-4" /> Hardware Security
                  </div>
                  <p className="text-sm font-bold text-white">Android Keystore</p>
                  <p className="text-xs text-slate-400">MasterKey wrapping, BiometricPrompt, Secure Vault</p>
                </div>

                <div className="bg-[#102B52]/50 border border-slate-800/80 rounded-xl p-4 space-y-2 hover:border-[#F47B20]/40 transition-colors">
                  <div className="flex items-center gap-2 text-xs font-semibold text-[#F47B20]">
                    <Cpu className="w-4 h-4" /> On-Device Intelligence
                  </div>
                  <p className="text-sm font-bold text-white">ML Kit OCR + TFLite</p>
                  <p className="text-xs text-slate-400">Downloadable plugin modules for OCR & Semantic Search</p>
                </div>

                <div className="bg-[#102B52]/50 border border-slate-800/80 rounded-xl p-4 space-y-2 hover:border-[#F47B20]/40 transition-colors">
                  <div className="flex items-center gap-2 text-xs font-semibold text-[#5BC0EB]">
                    <Cloud className="w-4 h-4" /> Cloud Architecture
                  </div>
                  <p className="text-sm font-bold text-white">Google Drive Core + SPI</p>
                  <p className="text-xs text-slate-400">REST API + Credential Manager; OneDrive/Dropbox/S3 plugins</p>
                </div>

                <div className="bg-[#102B52]/50 border border-slate-800/80 rounded-xl p-4 space-y-2 hover:border-[#F47B20]/40 transition-colors">
                  <div className="flex items-center gap-2 text-xs font-semibold text-[#D4A95A]">
                    <Zap className="w-4 h-4" /> Background Execution
                  </div>
                  <p className="text-sm font-bold text-white">WorkManager</p>
                  <p className="text-xs text-slate-400">Battery-friendly constraints, Periodic file indexing & sync</p>
                </div>

                <div className="bg-[#102B52]/50 border border-slate-800/80 rounded-xl p-4 space-y-2 hover:border-[#F47B20]/40 transition-colors">
                  <div className="flex items-center gap-2 text-xs font-semibold text-[#3FA34D]">
                    <Sparkles className="w-4 h-4" /> Dependency Injection
                  </div>
                  <p className="text-sm font-bold text-white">Hilt / Dagger</p>
                  <p className="text-xs text-slate-400">Singleton, ViewModelScoped, PluginModule boundaries</p>
                </div>

                <div className="bg-[#102B52]/50 border border-slate-800/80 rounded-xl p-4 space-y-2 hover:border-[#F47B20]/40 transition-colors">
                  <div className="flex items-center gap-2 text-xs font-semibold text-[#F47B20]">
                    <Terminal className="w-4 h-4" /> Build & Quality
                  </div>
                  <p className="text-sm font-bold text-white">Gradle Kotlin DSL</p>
                  <p className="text-xs text-slate-400">GitHub Actions CI/CD (./gradlew assembleRelease)</p>
                </div>
              </div>
            </div>

            {/* Brand Colors Showcase */}
            <div className="bg-[#102B52]/30 border border-slate-800 rounded-xl p-5 space-y-3">
              <h4 className="text-xs font-bold uppercase tracking-wider text-slate-300">
                Official Brand Palette & Design Tokens
              </h4>
              <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
                <div className="flex items-center gap-3 p-2.5 rounded-lg bg-[#0b1728] border border-slate-800">
                  <span className="w-6 h-6 rounded-md bg-[#F47B20] shadow" />
                  <div>
                    <p className="text-xs font-bold text-white">Bhagwa Orange</p>
                    <p className="text-[10px] font-mono text-slate-400">#F47B20</p>
                  </div>
                </div>
                <div className="flex items-center gap-3 p-2.5 rounded-lg bg-[#0b1728] border border-slate-800">
                  <span className="w-6 h-6 rounded-md bg-[#102B52] border border-slate-600 shadow" />
                  <div>
                    <p className="text-xs font-bold text-white">Cosmic Blue</p>
                    <p className="text-[10px] font-mono text-slate-400">#102B52</p>
                  </div>
                </div>
                <div className="flex items-center gap-3 p-2.5 rounded-lg bg-[#0b1728] border border-slate-800">
                  <span className="w-6 h-6 rounded-md bg-[#3FA34D] shadow" />
                  <div>
                    <p className="text-xs font-bold text-white">Emerald Green</p>
                    <p className="text-[10px] font-mono text-slate-400">#3FA34D</p>
                  </div>
                </div>
                <div className="flex items-center gap-3 p-2.5 rounded-lg bg-[#0b1728] border border-slate-800">
                  <span className="w-6 h-6 rounded-md bg-[#5BC0EB] shadow" />
                  <div>
                    <p className="text-xs font-bold text-white">Sky Cyan</p>
                    <p className="text-[10px] font-mono text-slate-400">#5BC0EB</p>
                  </div>
                </div>
                <div className="flex items-center gap-3 p-2.5 rounded-lg bg-[#0b1728] border border-slate-800">
                  <span className="w-6 h-6 rounded-md bg-[#D4A95A] shadow" />
                  <div>
                    <p className="text-xs font-bold text-white">Soft Gold</p>
                    <p className="text-[10px] font-mono text-slate-400">#D4A95A</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Tab 2: 19-Phase Roadmap */}
        {activeTab === 'phases' && (
          <div className="space-y-6 animate-fadeIn">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-base font-bold text-white">19 Phases Master Roadmap (v2.0)</h3>
                <p className="text-xs text-slate-400">Strict sequential execution model — zero steps skipped</p>
              </div>
              <div className="text-xs font-medium text-slate-300 bg-slate-800 px-3 py-1.5 rounded-lg border border-slate-700">
                1 / 19 Phases in Progress
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {PHASES.map((phase) => (
                <div
                  key={phase.id}
                  className={`p-4 rounded-xl border transition-all ${
                    phase.id === 1
                      ? 'bg-[#102B52]/80 border-[#F47B20] ring-1 ring-[#F47B20]/40'
                      : 'bg-[#102B52]/30 border-slate-800 hover:border-slate-700'
                  }`}
                >
                  <div className="flex items-start justify-between gap-2 mb-2">
                    <div className="flex items-center gap-2">
                      <span
                        className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${
                          phase.id === 1
                            ? 'bg-[#F47B20] text-white'
                            : 'bg-slate-800 text-slate-400'
                        }`}
                      >
                        {phase.id}
                      </span>
                      <h4 className="text-sm font-bold text-white">{phase.title}</h4>
                    </div>
                    <span
                      className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${
                        phase.id === 1
                          ? 'bg-[#F47B20]/20 text-[#F47B20] border border-[#F47B20]/40 animate-pulse'
                          : 'bg-slate-800 text-slate-400'
                      }`}
                    >
                      {phase.status}
                    </span>
                  </div>

                  <p className="text-xs text-slate-300 mb-3">{phase.description}</p>

                  <div className="space-y-1.5 pt-2 border-t border-slate-800/60">
                    <p className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                      Key Deliverables:
                    </p>
                    <ul className="grid grid-cols-1 sm:grid-cols-2 gap-1">
                      {phase.keyDeliverables.map((item, idx) => (
                        <li key={idx} className="text-[11px] text-slate-300 flex items-center gap-1.5">
                          <CheckCircle2 className="w-3 h-3 text-[#3FA34D] shrink-0" />
                          <span className="truncate">{item}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Tab 3: Phase 1 Technical Research */}
        {activeTab === 'research' && (
          <div className="space-y-6 animate-fadeIn">
            <div className="bg-[#102B52]/40 border border-slate-800 rounded-2xl p-6 space-y-6">
              <div className="flex items-center gap-3">
                <div className="p-2.5 rounded-xl bg-[#F47B20]/20 text-[#F47B20] border border-[#F47B20]/30">
                  <BookOpen className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-lg font-bold text-white">Phase 1: Technical Research & Verification [No Code]</h3>
                  <p className="text-xs text-slate-400">Formal engineering review ready for your approval before Phase 2</p>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* 1. Security & Storage */}
                <div className="bg-[#0b1728] p-5 rounded-xl border border-slate-800 space-y-3">
                  <div className="flex items-center gap-2 text-sm font-bold text-[#D4A95A]">
                    <Lock className="w-4 h-4" /> 1. Security & Storage Architecture
                  </div>
                  <ul className="text-xs text-slate-300 space-y-2 list-disc list-inside">
                    <li><strong className="text-white">Android Keystore:</strong> MasterKey generated with AES-256-GCM in StrongBox / TEE.</li>
                    <li><strong className="text-white">Room + SQLCipher:</strong> Passphrase derived dynamically via Keystore; zero plaintext keys stored in memory or SharedPreferences.</li>
                    <li><strong className="text-white">Secure Vault:</strong> Chunked stream encryption with random 96-bit IV per file to prevent memory exhaustion on large video/photo payloads.</li>
                  </ul>
                </div>

                {/* 2. Search & AI Plugin Separation */}
                <div className="bg-[#0b1728] p-5 rounded-xl border border-slate-800 space-y-3">
                  <div className="flex items-center gap-2 text-sm font-bold text-[#5BC0EB]">
                    <Search className="w-4 h-4" /> 2. Dual Search Engine Architecture
                  </div>
                  <ul className="text-xs text-slate-300 space-y-2 list-disc list-inside">
                    <li><strong className="text-white">Core FTS:</strong> Instant sub-10ms sqlite FTS4/FTS5 indexed searches over filenames, mime types, and manual tags.</li>
                    <li><strong className="text-white">Semantic AI Plugin:</strong> 100% on-device TFLite embeddings with cosine similarity matching; strictly additive and never replaces Core Search.</li>
                    <li><strong className="text-white">OCR Engine:</strong> ML Kit Text Recognition delivered as an on-demand plugin module to keep the base APK ultralight.</li>
                  </ul>
                </div>

                {/* 3. Cloud & Background Sync */}
                <div className="bg-[#0b1728] p-5 rounded-xl border border-slate-800 space-y-3">
                  <div className="flex items-center gap-2 text-sm font-bold text-[#3FA34D]">
                    <Cloud className="w-4 h-4" /> 3. Cloud & Background Execution
                  </div>
                  <ul className="text-xs text-slate-300 space-y-2 list-disc list-inside">
                    <li><strong className="text-white">Google Drive Core:</strong> Standard REST API with Credential Manager integration for seamless token refresh.</li>
                    <li><strong className="text-white">Cloud Driver SPI:</strong> Uniform SPI contract for OneDrive, Dropbox, NextCloud, and S3-compatible endpoints.</li>
                    <li><strong className="text-white">WorkManager:</strong> Background indexing restricted to unmetered network & charging constraints.</li>
                  </ul>
                </div>

                {/* 4. Performance & Cold Start */}
                <div className="bg-[#0b1728] p-5 rounded-xl border border-slate-800 space-y-3">
                  <div className="flex items-center gap-2 text-sm font-bold text-[#F47B20]">
                    <Zap className="w-4 h-4" /> 4. Performance & Cold-Start Budget
                  </div>
                  <ul className="text-xs text-slate-300 space-y-2 list-disc list-inside">
                    <li><strong className="text-white">Cold Start Target:</strong> Under 3.0s (strictly within the &lt;10s threshold).</li>
                    <li><strong className="text-white">App Startup Initializer:</strong> Defer non-critical SDKs, lazy-initialize SQLCipher database on first query.</li>
                    <li><strong className="text-white">R8 & Baseline Profiles:</strong> Pre-compiled AOT hot-paths for immediate Compose render.</li>
                  </ul>
                </div>
              </div>

              {/* Approval Box */}
              <div className="p-4 rounded-xl bg-[#F47B20]/10 border border-[#F47B20]/30 flex flex-col md:flex-row items-center justify-between gap-4">
                <div className="space-y-1">
                  <p className="text-sm font-bold text-white flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 text-[#3FA34D]" />
                    Phase 1 Research Complete — Awaiting Approval
                  </p>
                  <p className="text-xs text-slate-300">
                    Following our Golden Rule, Phase 2 (Architecture Freeze & Module Graph) will begin as soon as you approve.
                  </p>
                </div>
                <div className="px-4 py-2 rounded-xl bg-[#F47B20] text-white text-xs font-bold shadow-md shadow-[#F47B20]/30 whitespace-nowrap">
                  Ready for Phase 2 Approval
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Tab 4: GitHub Sync */}
        {activeTab === 'github' && (
          <div className="space-y-6 animate-fadeIn">
            <div className="bg-[#102B52]/40 border border-slate-800 rounded-2xl p-6 space-y-6">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="p-2.5 rounded-xl bg-[#5BC0EB]/20 text-[#5BC0EB] border border-[#5BC0EB]/30">
                    <GitBranch className="w-6 h-6" />
                  </div>
                  <div>
                    <h3 className="text-lg font-bold text-white">GitHub Repository Synchronization</h3>
                    <p className="text-xs text-slate-400">Push to target: <span className="font-mono text-white">awasthisach/Top-VVF</span></p>
                  </div>
                </div>
                <span className="text-xs font-mono bg-[#3FA34D]/20 text-[#3FA34D] border border-[#3FA34D]/30 px-3 py-1 rounded-lg">
                  Origin Configured
                </span>
              </div>

              {/* Instructions */}
              <div className="space-y-4">
                <p className="text-xs text-slate-300">
                  The local Git workspace is initialized with branch <code className="text-[#5BC0EB] font-mono">main</code> and remote set to:
                </p>

                <div className="bg-[#0b1728] p-3 rounded-xl border border-slate-800 flex items-center justify-between font-mono text-xs text-slate-200">
                  <span>https://github.com/awasthisach/Top-VVF.git</span>
                  <button
                    onClick={() => copyToClipboard('https://github.com/awasthisach/Top-VVF.git', 'url')}
                    className="p-1.5 rounded-lg hover:bg-slate-800 text-slate-400 hover:text-white transition"
                    title="Copy URL"
                  >
                    {copiedCmd === 'url' ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>

                {/* Git Push Commands */}
                <div className="space-y-2">
                  <h4 className="text-xs font-bold text-slate-300 uppercase tracking-wider">
                    Pushing via Personal Access Token (PAT)
                  </h4>
                  <div className="bg-[#0b1728] p-4 rounded-xl border border-slate-800 space-y-2 text-xs font-mono">
                    <div className="flex items-center justify-between text-slate-400 border-b border-slate-800/80 pb-2">
                      <span>Terminal Commands</span>
                      <button
                        onClick={() => copyToClipboard('git branch -M main\ngit add .\ngit commit -m "feat: VVF Smart Manager Phase 1 & Full Architecture Setup"\ngit push -u origin main', 'cmds')}
                        className="flex items-center gap-1 text-[11px] text-[#5BC0EB] hover:underline"
                      >
                        {copiedCmd === 'cmds' ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                        Copy All
                      </button>
                    </div>
                    <pre className="text-slate-300 overflow-x-auto py-1">
{`# 1. Ensure on main branch and stage files
git branch -M main
git add .
git commit -m "feat: VVF Smart Manager Phase 1 & Full Architecture Setup"

# 2. Push to GitHub (enter your GitHub PAT token when prompted)
git push -u origin main`}
                    </pre>
                  </div>
                </div>

                {/* Direct Export from AI Studio UI */}
                <div className="p-4 rounded-xl bg-[#0b1728] border border-slate-800 space-y-2">
                  <h4 className="text-xs font-bold text-[#D4A95A] flex items-center gap-1.5">
                    <ExternalLink className="w-4 h-4" /> Export Directly via AI Studio Menu
                  </h4>
                  <p className="text-xs text-slate-300">
                    You can also export this repository directly to GitHub without manually entering tokens:
                  </p>
                  <ol className="text-xs text-slate-400 list-decimal list-inside space-y-1">
                    <li>Click the <strong>Settings / Export</strong> menu icon in the top right header of Google AI Studio.</li>
                    <li>Select <strong>Export to GitHub</strong>.</li>
                    <li>Choose or confirm repository <code className="text-slate-200">awasthisach/Top-VVF</code>.</li>
                  </ol>
                </div>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-800/80 bg-[#102B52]/60 px-6 py-4 mt-auto text-center text-xs text-slate-400">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-2">
          <span>VVF Smart Manager &copy; 2026. Built with Clean Architecture &amp; Kotlin Frozen Stack.</span>
          <span className="text-[11px] text-slate-500 font-mono">Package: com.vvf.smartmanager | Status: Phase 1 Research</span>
        </div>
      </footer>
    </div>
  );
}
