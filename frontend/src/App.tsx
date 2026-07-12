import './index.css';
import { useState, useEffect, useRef } from 'react';
import { Terminal, Play, Loader2, GitBranch, Plus, FolderGit2, Server, Lock, Mail, User, LogOut, History } from 'lucide-react';

interface Project {
  id: number;
  name: string;
  githubRepoUrl: string;
  branch: string;
}

interface Deployment {
  id: number;
  status: string;
  createdAt: string;
}

export default function App() {
  // --- AUTHENTICATION STATE ---
  const [token, setToken] = useState<string | null>(localStorage.getItem('jwt'));
  const [authMode, setAuthMode] = useState<'LOGIN' | 'REGISTER'>('LOGIN');
  const [authForm, setAuthForm] = useState({ email: '', password: '', fullName: '' });
  const [authError, setAuthError] = useState('');
  const [isAuthLoading, setIsAuthLoading] = useState(false);

  // --- DASHBOARD STATE ---
  const [projects, setProjects] = useState<Project[]>([]);
  const [activeProject, setActiveProject] = useState<Project | null>(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [newProject, setNewProject] = useState({ name: '', githubRepoUrl: '', branch: 'main' });
  const [deploymentHistory, setDeploymentHistory] = useState<Deployment[]>([]);

  // --- TERMINAL STATE ---
  const [status, setStatus] = useState('IDLE');
  const [logs, setLogs] = useState<string[]>([]);
  const terminalEndRef = useRef<HTMLDivElement>(null);

  // 1. DEFINE LOGOUT EARLY
  const handleLogout = () => {
    localStorage.removeItem('jwt');
    setToken(null);
    setActiveProject(null);
    setProjects([]);
  };

  // 2. FETCH PROJECTS
  useEffect(() => {
    if (!token) return;
    
    fetch('/api/projects', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
      .then(res => {
        if (res.status === 403) handleLogout(); 
        return res.json();
      })
      .then(data => setProjects(data))
      .catch(err => console.error("Failed to load projects", err));
  }, [token]);

  // 3. FETCH DEPLOYMENT HISTORY
  // 3. FETCH DEPLOYMENT HISTORY
  useEffect(() => {
    if (!activeProject || !token) return;

    fetch(`/api/deployments/project/${activeProject.id}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
      .then(res => {
        if (!res.ok) throw new Error("Backend endpoint missing or failed");
        return res.json();
      })
      .then(data => {
        // SAFETY CHECK: Only update if it's a real array. Otherwise, set to empty list!
        if (Array.isArray(data)) {
          setDeploymentHistory(data);
        } else {
          setDeploymentHistory([]);
        }
      })
      .catch(err => {
        console.error("Failed to load history", err);
        setDeploymentHistory([]); // Prevent UI crash by defaulting to empty array
      });
  }, [activeProject, token, status]);
  
  // Auto-scroll terminal
  useEffect(() => {
    terminalEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [logs]);

  // --- AUTHENTICATION LOGIC ---
  const handleAuthSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError('');
    setIsAuthLoading(true);

    const endpoint = authMode === 'LOGIN' ? '/api/auth/login' : '/api/auth/register';
    
    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(authForm)
      });

      const text = await res.text();
      let data;
      try {
        data = text ? JSON.parse(text) : {};
      } catch (err) {
        data = { message: `Backend returned an empty or invalid response. Status: ${res.status}` };
      }

      if (!res.ok) {
        throw new Error(data.message || `Authentication failed (Error ${res.status}). Check credentials.`);
      }

      if (authMode === 'LOGIN') {
        const extractedToken = data.token || data.jwtToken || data.accessToken;
        
        if (!extractedToken) {
          throw new Error("Backend accepted login, but React couldn't read the token name! Check your Java AuthResponse.java.");
        }

        localStorage.setItem('jwt', extractedToken);
        setToken(extractedToken);
      } else {
        setAuthMode('LOGIN');
        setAuthError('Account created successfully! Please sign in.');
      }
    } catch (err: any) {
      setAuthError(err.message);
    } finally {
      setIsAuthLoading(false);
    }
  };

  // --- PROJECT LOGIC ---
  const handleCreateProject = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await fetch('/api/projects', {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}` 
        },
        body: JSON.stringify(newProject)
      });

      if (!res.ok) throw new Error("Failed to save project");

      const savedProject = await res.json();
      setProjects([...projects, savedProject]);
      setShowCreateForm(false);
      setNewProject({ name: '', githubRepoUrl: '', branch: 'main' });
    } catch (err: any) {
      alert(err.message);
    }
  };

  const triggerDeployment = async (projectId: number) => {
    setStatus('RUNNING');
    setLogs(['[SYSTEM] Initiating deployment pipeline...', `[SYSTEM] Authenticating secure request...`]);

    try {
      const response = await fetch(`/api/projects/${projectId}/deploy`, { 
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      });
      
      if (!response.ok) throw new Error(`Server responded with status: ${response.status}`);
      
      const deploymentId = await response.text(); 
      
      const eventSource = new EventSource(`/api/deployments/${deploymentId}/logs`);

      eventSource.onmessage = (event) => {
        setLogs(prevLogs => [...prevLogs, event.data]);
        if (event.data.includes('SUCCESS!')) {
          setStatus('SUCCESS');
          eventSource.close();
        } else if (event.data.includes('FAILED')) {
          setStatus('FAILED');
          eventSource.close();
        }
      };

      eventSource.onerror = () => {
        setLogs(prevLogs => [...prevLogs, '[SYSTEM] Lost connection to live log stream.']);
        setStatus('FAILED');
        eventSource.close();
      };
    } catch (error: any) {
      setLogs(prevLogs => [...prevLogs, `[ERROR] Failed: ${error.message}`]);
      setStatus('FAILED');
    }
  };

  const getStatusColor = () => {
    if (status === 'SUCCESS') return 'text-emerald-400';
    if (status === 'FAILED') return 'text-red-400';
    if (status === 'RUNNING') return 'text-blue-400';
    return 'text-gray-400';
  };

  // ==========================================
  // VIEW 1: AUTHENTICATION SCREEN
  // ==========================================
  if (!token) {
    return (
      <div className="min-h-screen bg-[#0A0A0A] text-gray-200 flex flex-col justify-center items-center p-6 selection:bg-blue-500/30">
        <div className="w-full max-w-md bg-[#111] border border-gray-800 rounded-2xl p-8 shadow-2xl">
          <div className="flex items-center justify-center gap-3 mb-8">
            <div className="bg-blue-500/10 p-3 rounded-xl border border-blue-500/20">
              <Server className="w-6 h-6 text-blue-400" />
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-white">DeployFlow</h1>
          </div>

          <h2 className="text-xl font-semibold text-center mb-6 text-gray-100">
            {authMode === 'LOGIN' ? 'Sign in to your account' : 'Create your secure account'}
          </h2>

          {authError && (
            <div className={`p-3 rounded-lg mb-6 text-sm text-center border ${authError.includes('success') ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-red-500/10 text-red-400 border-red-500/20'}`}>
              {authError}
            </div>
          )}

          <form onSubmit={handleAuthSubmit} className="space-y-4">
            {authMode === 'REGISTER' && (
              <div className="relative">
                <User className="w-5 h-5 absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" />
                <input required type="text" placeholder="Full Name" className="w-full bg-black border border-gray-800 rounded-lg py-2.5 pl-10 pr-4 text-white focus:border-blue-500 outline-none transition" value={authForm.fullName} onChange={e => setAuthForm({...authForm, fullName: e.target.value})} />
              </div>
            )}
            <div className="relative">
              <Mail className="w-5 h-5 absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" />
              <input required type="email" placeholder="Email Address" className="w-full bg-black border border-gray-800 rounded-lg py-2.5 pl-10 pr-4 text-white focus:border-blue-500 outline-none transition" value={authForm.email} onChange={e => setAuthForm({...authForm, email: e.target.value})} />
            </div>
            <div className="relative">
              <Lock className="w-5 h-5 absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" />
              <input required type="password" placeholder="Password" className="w-full bg-black border border-gray-800 rounded-lg py-2.5 pl-10 pr-4 text-white focus:border-blue-500 outline-none transition" value={authForm.password} onChange={e => setAuthForm({...authForm, password: e.target.value})} />
            </div>

            <button disabled={isAuthLoading} type="submit" className="w-full bg-white text-black font-semibold py-2.5 rounded-lg hover:bg-gray-200 transition flex justify-center items-center disabled:opacity-50 mt-6">
              {isAuthLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : (authMode === 'LOGIN' ? 'Sign In' : 'Create Account')}
            </button>
          </form>

          <div className="mt-6 text-center text-sm text-gray-500">
            {authMode === 'LOGIN' ? "Don't have an account? " : "Already have an account? "}
            <button onClick={() => { setAuthMode(authMode === 'LOGIN' ? 'REGISTER' : 'LOGIN'); setAuthError(''); }} className="text-blue-400 hover:text-blue-300 font-medium transition">
              {authMode === 'LOGIN' ? 'Sign up' : 'Sign in'}
            </button>
          </div>
        </div>
      </div>
    );
  }

  // ==========================================
  // VIEW 2: SECURE DASHBOARD
  // ==========================================
  return (
    <div className="min-h-screen bg-[#0A0A0A] text-gray-200 font-sans selection:bg-blue-500/30">
      <nav className="border-b border-gray-800 bg-[#111] px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3 cursor-pointer" onClick={() => setActiveProject(null)}>
          <div className="bg-blue-500/10 p-2 rounded-lg border border-blue-500/20">
            <Server className="w-5 h-5 text-blue-400" />
          </div>
          <h1 className="text-xl font-semibold tracking-tight text-white hover:text-blue-400 transition">DeployFlow</h1>
        </div>
        <button onClick={handleLogout} className="flex items-center gap-2 text-sm text-gray-400 hover:text-white transition bg-gray-900 px-3 py-1.5 rounded-md border border-gray-800">
          <LogOut className="w-4 h-4" /> Sign Out
        </button>
      </nav>

      <main className="max-w-7xl mx-auto p-6 mt-6">
        {!activeProject && (
          <div className="space-y-8">
            <div className="flex justify-between items-center">
              <h2 className="text-2xl font-semibold text-white">Your Projects</h2>
              <button onClick={() => setShowCreateForm(!showCreateForm)} className="flex items-center gap-2 bg-white text-black px-4 py-2 rounded-md font-medium hover:bg-gray-200 transition">
                <Plus className="w-4 h-4" /> Add New Project
              </button>
            </div>

            {showCreateForm && (
              <form onSubmit={handleCreateProject} className="bg-[#111] border border-gray-800 p-6 rounded-xl space-y-4">
                <h3 className="text-lg text-white font-medium mb-4">Import Git Repository</h3>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <input required placeholder="Project Name (e.g. My Blog)" className="bg-black border border-gray-700 rounded-md px-4 py-2 text-white focus:border-blue-500 outline-none" value={newProject.name} onChange={e => setNewProject({...newProject, name: e.target.value})} />
                  <input required placeholder="GitHub URL (https://github.com/...)" className="bg-black border border-gray-700 rounded-md px-4 py-2 text-white focus:border-blue-500 outline-none" value={newProject.githubRepoUrl} onChange={e => setNewProject({...newProject, githubRepoUrl: e.target.value})} />
                  <input required placeholder="Branch (e.g. main)" className="bg-black border border-gray-700 rounded-md px-4 py-2 text-white focus:border-blue-500 outline-none" value={newProject.branch} onChange={e => setNewProject({...newProject, branch: e.target.value})} />
                </div>
                <button type="submit" className="bg-blue-600 text-white px-4 py-2 rounded-md hover:bg-blue-700 transition">Save Project</button>
              </form>
            )}

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              {projects.map(p => (
                <div key={p.id} className="bg-[#111] border border-gray-800 p-6 rounded-xl hover:border-gray-600 transition cursor-pointer" onClick={() => { setActiveProject(p); setLogs([]); setStatus('IDLE'); }}>
                  <div className="flex items-center gap-3 mb-4">
                    <FolderGit2 className="text-gray-400 w-6 h-6" />
                    <h3 className="text-lg font-medium text-white">{p.name}</h3>
                  </div>
                  <div className="text-sm text-gray-500 flex items-center gap-2 mb-2">
                    <GitBranch className="w-4 h-4" /> {p.branch}
                  </div>
                  <p className="text-xs text-gray-600 truncate">{p.githubRepoUrl}</p>
                </div>
              ))}
              {projects.length === 0 && !showCreateForm && <p className="text-gray-500">No projects found. Create one to get started!</p>}
            </div>
          </div>
        )}

        {activeProject && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* LEFT COLUMN: Controls & History */}
            <div className="lg:col-span-1 space-y-6">
              <div className="bg-[#111] border border-gray-800 rounded-xl p-6">
                <h2 className="text-lg font-medium text-white mb-2">{activeProject.name}</h2>
                <p className="text-xs text-gray-500 mb-6 truncate">{activeProject.githubRepoUrl}</p>
                
                <button onClick={() => triggerDeployment(activeProject.id)} disabled={status === 'RUNNING'} className="w-full flex items-center justify-center gap-2 bg-white text-black hover:bg-gray-200 disabled:opacity-50 font-medium py-2.5 px-4 rounded-lg transition-all">
                  {status === 'RUNNING' ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4 fill-current" />}
                  Deploy Now
                </button>
              </div>

              {/* DEPLOYMENT HISTORY SIDEBAR */}
              <div className="bg-[#111] border border-gray-800 rounded-xl p-6">
                <div className="flex items-center gap-2 mb-4">
                  <History className="w-4 h-4 text-gray-400" />
                  <h3 className="text-sm font-medium text-gray-400 uppercase tracking-wider">Deployment History</h3>
                </div>
                
                <div className="space-y-3 max-h-[400px] overflow-y-auto pr-2 custom-scrollbar">
                  {deploymentHistory.length === 0 ? (
                    <p className="text-xs text-gray-600 italic">No previous deployments found.</p>
                  ) : (
                    deploymentHistory.map((dep, index) => (
                      <div key={dep.id} className="flex flex-col gap-2 bg-[#0c0c0c] border border-gray-800/50 p-3 rounded-lg hover:border-gray-700 transition">
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <span className="text-gray-500 font-mono text-xs">#{deploymentHistory.length - index}</span>
                            <span className="text-sm text-gray-300">Manual Deploy</span>
                          </div>
                          <span className={`text-[10px] uppercase font-mono font-bold px-2 py-1 rounded bg-black/80 border border-gray-800 
                            ${dep.status === 'SUCCESS' ? 'text-emerald-400' : 
                              dep.status === 'FAILED' ? 'text-red-400' : 
                              'text-blue-400'}`}>
                            {dep.status}
                          </span>
                        </div>
                        <div className="text-xs text-gray-600 font-mono">
                          {new Date(dep.createdAt).toLocaleString()}
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>

            {/* RIGHT COLUMN: Terminal */}
            <div className="lg:col-span-2 flex flex-col h-[650px] bg-[#0c0c0c] border border-gray-800 rounded-xl overflow-hidden shadow-2xl">
              <div className="bg-[#1a1a1a] border-b border-gray-800 px-4 py-3 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Terminal className="w-4 h-4 text-gray-400" />
                  <span className="text-sm font-medium text-gray-300">Live Logs: {activeProject.name}</span>
                </div>
                <div className={`text-xs font-mono font-bold px-2 py-1 rounded bg-black/50 border border-gray-800 ${getStatusColor()}`}>
                  {status}
                </div>
              </div>
              <div className="flex-1 overflow-y-auto p-4 font-mono text-sm">
                {logs.length === 0 ? (
                  <div className="text-gray-600 italic mt-4 text-center">Ready to deploy. Click 'Deploy Now' to start pipeline.</div>
                ) : (
                  logs.map((log, index) => (
                    <div key={index} className="mb-1">
                      <span className={log.includes('ERROR') || log.includes('FAILED') ? 'text-red-400' : log.includes('SUCCESS') ? 'text-emerald-400' : 'text-gray-300'}>
                        {log}
                      </span>
                    </div>
                  ))
                )}
                <div ref={terminalEndRef} />
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}