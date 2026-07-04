import React, { useState, useEffect } from 'react';
import axios from 'axios';

interface Log {
  id: number;
  message: string;
  createdAt: string;
}

export default function App() {
  const [currentProject] = useState("deployflow-api");
  const [status, setStatus] = useState("LOADING");
  const [logs, setLogs] = useState<Log[]>([]);
  const [error, setError] = useState<string | null>(null);
  
  // 1. MAKE THE DEPLOYMENT ID DYNAMIC (Starting at 5)
  const [deploymentId, setDeploymentId] = useState(5);
  // 2. ADD A LOADING STATE FOR THE BUTTON
  const [isDeploying, setIsDeploying] = useState(false);

  useEffect(() => {
    const fetchLogs = () => {
      axios.get(`http://localhost:8080/api/deployments/${deploymentId}/logs`)
        .then((response) => {
          setLogs(response.data);
          
          // Dynamically calculate status based on logs
          if (response.data.length === 0) {
             setStatus("WAITING FOR LOGS...");
          } else if (response.data[response.data.length - 1].message.includes("Routing traffic")) {
             setStatus("SUCCESS");
          } else {
             setStatus("BUILDING...");
          }
          setError(null);
        })
        .catch((err) => {
          console.error("Error fetching logs:", err);
          setError("Could not connect to the backend server.");
          setStatus("FAILED");
        });
    };

    fetchLogs();
    const interval = setInterval(fetchLogs, 3000);
    return () => clearInterval(interval);
  }, [deploymentId]); // 3. RE-RUN THIS EFFECT WHENEVER THE ID CHANGES

  // 4. THE DEPLOY BUTTON LOGIC
  // THE REAL DEPLOY BUTTON LOGIC
  const handleDeploy = () => {
    setIsDeploying(true);
    setLogs([]); // Clear the terminal
    setStatus("STARTING...");

    // Assuming your project ID in the database is 1
    const projectId = 1;

    axios.post(`http://localhost:8080/api/deployments/project/${projectId}`)
      .then((response) => {
        // Spring Boot just created a new deployment and sent us the new ID!
        const newId = response.data.id;
        setDeploymentId(newId); // Tell React to start listening to this new ID
        setIsDeploying(false);
      })
      .catch((err) => {
        console.error("Failed to trigger deployment:", err);
        setError("Failed to start deployment on the server.");
        setIsDeploying(false);
        setStatus("FAILED");
      });
  };

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100 font-sans antialiased">
      {/* Top Navigation Bar */}
      <nav className="border-b border-gray-800 bg-gray-900/50 backdrop-blur px-6 py-4 flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <div className="h-8 w-8 bg-indigo-600 rounded-lg flex items-center justify-center font-bold text-white shadow-lg shadow-indigo-500/20">
            DF
          </div>
          <span className="text-lg font-semibold tracking-tight">DeployFlow</span>
        </div>
        
        {/* THE NEW DEPLOY BUTTON */}
        <div className="flex items-center space-x-4">
          <button 
            onClick={handleDeploy}
            disabled={isDeploying || status === "BUILDING..."}
            className="bg-indigo-600 hover:bg-indigo-500 text-white px-5 py-2 rounded-md font-medium text-sm shadow-[0_0_15px_rgba(79,70,229,0.3)] hover:shadow-[0_0_25px_rgba(79,70,229,0.5)] transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center space-x-2"
          >
            {isDeploying ? (
              <>
                <svg className="animate-spin h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path></svg>
                <span>Initializing...</span>
              </>
            ) : (
              <>
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path></svg>
                <span>Deploy Now</span>
              </>
            )}
          </button>
        </div>
      </nav>

      {/* Main Content Area */}
      <main className="max-w-6xl mx-auto p-6 space-y-6">
        
        {error && (
          <div className="bg-red-900/20 border border-red-500/30 rounded-xl p-4 text-sm text-red-400 font-medium">
            ⚠️ {error}
          </div>
        )}

        {/* Project Header Card */}
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 shadow-sm">
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div>
              <div className="flex items-center space-x-3">
                <h1 className="text-2xl font-bold tracking-tight text-white">{currentProject}</h1>
                <span className={`px-2.5 py-0.5 text-xs font-semibold rounded-full border transition-colors ${
                  status === 'SUCCESS' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' :
                  status === 'FAILED' ? 'bg-red-500/10 text-red-400 border-red-500/20' :
                  'bg-yellow-500/10 text-yellow-400 border-yellow-500/20 animate-pulse'
                }`}>
                  {status}
                </span>
              </div>
              <p className="text-sm text-gray-400 mt-1">Production Deployment • Branch: <code className="text-indigo-400 bg-gray-950 px-1 py-0.5 rounded text-xs font-mono">main</code></p>
            </div>
            
            <div className="text-sm text-gray-400 md:text-right">
              <p>Deployment ID: <span className="font-mono text-gray-200 text-xs">id-{deploymentId}</span></p>
              <p className="mt-0.5">Triggered by: <span className="text-gray-200 font-medium">{deploymentId === 5 ? "GitHub Webhook" : "Manual Trigger"}</span></p>
            </div>
          </div>
        </div>

        {/* Live Build Terminal Panel */}
        <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden shadow-2xl">
          <div className="bg-gray-950 px-4 py-3 border-b border-gray-800 flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <div className="h-3 w-3 rounded-full bg-red-500/40"></div>
              <div className="h-3 w-3 rounded-full bg-yellow-500/40"></div>
              <div className="h-3 w-3 rounded-full bg-green-500/40"></div>
              <span className="text-xs font-mono text-gray-400 ml-2">build-logs.log</span>
            </div>
            <span className="text-xs font-mono text-gray-500 flex items-center">
              {status === "BUILDING..." && <span className="w-2 h-2 bg-yellow-500 rounded-full mr-2 animate-ping"></span>}
              Live Streaming Active
            </span>
          </div>

          <div className="p-4 bg-gray-950 font-mono text-sm leading-relaxed h-[400px] overflow-y-auto space-y-2 scrollbar-thin scrollbar-thumb-gray-800">
            {logs.length === 0 ? (
              <div className="text-gray-500 italic text-center pt-8">
                {status === "WAITING FOR LOGS..." ? "Listening for incoming build logs..." : "No deployment logs found for this ID..."}
              </div>
            ) : (
              logs.map((log) => (
                <div key={log.id} className="flex items-start hover:bg-gray-900/40 py-0.5 px-1 rounded transition-colors group animate-fade-in-up">
                  <span className="text-gray-600 select-none pr-4 text-xs pt-0.5 w-24 shrink-0">
                    {new Date(log.createdAt).toLocaleTimeString()}
                  </span>
                  <span className="text-gray-300 group-hover:text-white break-all">
                    {log.message.includes("Routing traffic") ? (
                      <span className="text-indigo-400 font-medium">{log.message}</span>
                    ) : log.message.includes("Successfully") || log.message.includes("up to date") ? (
                      <span className="text-emerald-400">{log.message}</span>
                    ) : (
                      log.message
                    )}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>

      </main>
    </div>
  );
}