import { useState, useEffect } from 'react';
import { User, AuthState } from '../types';

export const useAuth = () => {
  const [authState, setAuthState] = useState<AuthState>({
    user: null,
    isAuthenticated: false,
  });

  useEffect(() => {
    const savedUser = localStorage.getItem('bankingUser');
    if (savedUser) {
      setAuthState({
        user: JSON.parse(savedUser),
        isAuthenticated: true,
      });
    }
  }, []);

  const login = (username: string, password: string): boolean => {
    const users = JSON.parse(localStorage.getItem('bankingUsers') || '[]');
    const user = users.find((u: any) => u.username === username && u.password === password);
    
    if (user) {
      const { password: _, ...userWithoutPassword } = user;
      setAuthState({
        user: userWithoutPassword,
        isAuthenticated: true,
      });
      localStorage.setItem('bankingUser', JSON.stringify(userWithoutPassword));
      return true;
    }
    return false;
  };

  const register = (userData: {
    username: string;
    password: string;
    email: string;
    fullName: string;
  }): boolean => {
    const users = JSON.parse(localStorage.getItem('bankingUsers') || '[]');
    
    if (users.find((u: any) => u.username === userData.username)) {
      return false;
    }

    const newUser = {
      id: Date.now().toString(),
      ...userData,
      createdAt: new Date().toISOString(),
    };

    users.push(newUser);
    localStorage.setItem('bankingUsers', JSON.stringify(users));

    // Create default accounts
    const accounts = JSON.parse(localStorage.getItem('bankingAccounts') || '[]');
    const checkingAccount = {
      id: Date.now().toString(),
      userId: newUser.id,
      accountNumber: generateAccountNumber(),
      accountType: 'checking' as const,
      balance: 1000,
      createdAt: new Date().toISOString(),
    };
    
    const savingsAccount = {
      id: (Date.now() + 1).toString(),
      userId: newUser.id,
      accountNumber: generateAccountNumber(),
      accountType: 'savings' as const,
      balance: 5000,
      createdAt: new Date().toISOString(),
    };

    accounts.push(checkingAccount, savingsAccount);
    localStorage.setItem('bankingAccounts', JSON.stringify(accounts));

    return true;
  };

  const logout = () => {
    setAuthState({
      user: null,
      isAuthenticated: false,
    });
    localStorage.removeItem('bankingUser');
  };

  return {
    ...authState,
    login,
    register,
    logout,
  };
};

const generateAccountNumber = (): string => {
  return Math.random().toString().slice(2, 12).padStart(10, '0');
};