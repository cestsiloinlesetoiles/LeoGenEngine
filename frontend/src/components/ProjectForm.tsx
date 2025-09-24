import React, { useState } from 'react';
import { GenerationRequest } from '../types';

interface ProjectFormProps {
  onSubmit: (request: GenerationRequest) => void;
  isLoading: boolean;
  isConnected: boolean;
}

const ProjectForm: React.FC<ProjectFormProps> = ({ onSubmit, isLoading, isConnected }) => {
  const [projectName, setProjectName] = useState('');
  const [projectDescription, setProjectDescription] = useState('');
  const [workspacePath, setWorkspacePath] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!projectName.trim() || !projectDescription.trim()) {
      alert('Please fill in both project name and description');
      return;
    }

    if (!isConnected) {
      alert('Please wait for WebSocket connection before starting generation');
      return;
    }

    const request: GenerationRequest = {
      projectName: projectName.trim(),
      projectDescription: projectDescription.trim(),
      workspacePath: workspacePath.trim() || undefined,
    };

    onSubmit(request);
  };

  const exampleProjects = [
    {
      name: 'simple_token',
      description: 'A basic token system with mint, transfer, and burn functionality'
    },
    {
      name: 'simple_dex',
      description: 'A simple decentralized exchange with AMM, swaps, and liquidity pools'
    },
  ];

  const loadExample = (example: typeof exampleProjects[0]) => {
    setProjectName(example.name);
    setProjectDescription(example.description);
  };

  return (
    <div className="h-full bg-leo-dark p-6">
      <div className="max-w-2xl mx-auto">
        <div className="mb-6">
          <h1 className="text-3xl font-bold text-white mb-2">LeoForge</h1>
          <p className="text-gray-400">Generate Leo code with AI assistance and real-time streaming</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label htmlFor="projectName" className="block text-sm font-medium text-gray-300 mb-2">
              Project Name *
            </label>
            <input
              type="text"
              id="projectName"
              value={projectName}
              onChange={(e) => setProjectName(e.target.value)}
              placeholder="e.g., simple_token"
              className="w-full px-3 py-2 bg-leo-gray border border-gray-600 rounded-md text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-leo-blue"
              pattern="^[a-z][a-z0-9_]*$"
              title="Must start with lowercase letter and contain only lowercase letters, numbers, and underscores"
              disabled={isLoading}
            />
            <p className="text-xs text-gray-500 mt-1">
              Must start with lowercase letter and contain only lowercase letters, numbers, and underscores
            </p>
          </div>

          <div>
            <label htmlFor="projectDescription" className="block text-sm font-medium text-gray-300 mb-2">
              Project Description *
            </label>
            <textarea
              id="projectDescription"
              value={projectDescription}
              onChange={(e) => setProjectDescription(e.target.value)}
              placeholder="Describe what your Leo program should do..."
              rows={4}
              className="w-full px-3 py-2 bg-leo-gray border border-gray-600 rounded-md text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-leo-blue resize-none"
              disabled={isLoading}
            />
          </div>

          <div>
            <label htmlFor="workspacePath" className="block text-sm font-medium text-gray-300 mb-2">
              Workspace Path (Optional)
            </label>
            <input
              type="text"
              id="workspacePath"
              value={workspacePath}
              onChange={(e) => setWorkspacePath(e.target.value)}
              placeholder="Leave empty to use default workspace"
              className="w-full px-3 py-2 bg-leo-gray border border-gray-600 rounded-md text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-leo-blue"
              disabled={isLoading}
            />
          </div>

          <div className="flex items-center gap-4">
            <button
              type="submit"
              disabled={isLoading || !isConnected}
              className={`px-6 py-2 rounded-md font-medium transition-colors ${
                isLoading || !isConnected
                  ? 'bg-gray-600 text-gray-400 cursor-not-allowed'
                  : 'bg-leo-blue hover:bg-blue-700 text-white'
              }`}
            >
              {isLoading ? 'Generating...' : 'Generate Leo Code'}
            </button>

            <div className="flex items-center gap-2">
              <div className={`w-2 h-2 rounded-full ${isConnected ? 'bg-green-400' : 'bg-red-400'}`}></div>
              <span className="text-sm text-gray-400">
                {isConnected ? 'Connected' : 'Connecting...'}
              </span>
            </div>
          </div>
        </form>

        <div className="mt-8">
          <h3 className="text-lg font-medium text-white mb-4">Example Projects</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {exampleProjects.map((example, index) => (
              <div
                key={index}
                className="p-4 bg-leo-gray rounded-md border border-gray-600 hover:border-gray-500 transition-colors cursor-pointer"
                onClick={() => loadExample(example)}
              >
                <h4 className="font-medium text-white mb-2">{example.name}</h4>
                <p className="text-sm text-gray-400">{example.description}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="mt-8 p-4 bg-blue-900 bg-opacity-50 rounded-md border border-blue-700">
          <h3 className="text-lg font-medium text-blue-200 mb-2">How it works</h3>
          <ul className="text-sm text-blue-200 space-y-1">
            <li>• AI analyzes your project description</li>
            <li>• Code is generated in real-time with streaming</li>
            <li>• Automatic compilation and error fixing</li>
            <li>• See live progress with detailed events</li>
          </ul>
        </div>
      </div>
    </div>
  );
};

export default ProjectForm;
