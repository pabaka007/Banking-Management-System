import React, { useState } from 'react';
import { Login } from './components/Login';
import { Register } from './components/Register';
import { Dashboard } from './components/Dashboard';
import { useAuth } from './hooks/useAuth';
import { useAccounts } from './hooks/useAccounts';

function App() {
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login');
  const { user, isAuthenticated, login, register, logout } = useAuth();
  const { accounts, transactions, loading, transfer } = useAccounts(user?.id || null);

  if (!isAuthenticated) {
    return authMode === 'login' ? (
      <Login
        onLogin={login}
        onSwitchToRegister={() => setAuthMode('register')}
      />
    ) : (
      <Register
        onRegister={register}
        onSwitchToLogin={() => setAuthMode('login')}
      />
    );
  }

  return (
    <Dashboard
      user={user!}
      accounts={accounts}
      transactions={transactions}
      onLogout={logout}
      onTransfer={transfer}
      loading={loading}
    />
  );
}

export default App;