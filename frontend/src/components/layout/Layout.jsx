import { useState } from 'react';
import Sidebar from './Sidebar';
import Header from './Header';

export default function Layout({ children }) {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-50 to-gray-100/90">
      <Sidebar
        collapsed={sidebarCollapsed}
        onToggle={() => setSidebarCollapsed(!sidebarCollapsed)}
      />
      <div
        className={`min-h-screen transition-all duration-300 ${
          sidebarCollapsed ? 'ml-[72px]' : 'ml-[260px]'
        }`}
      >
        <Header />
        <main className="mx-auto max-w-[1600px] px-5 py-6 sm:px-6 lg:px-8 lg:py-8">
          {children}
        </main>
      </div>
    </div>
  );
}
