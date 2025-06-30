export interface User {
  id: string;
  username: string;
  email: string;
  fullName: string;
  createdAt: string;
}

export interface Account {
  id: string;
  userId: string;
  accountNumber: string;
  accountType: 'checking' | 'savings';
  balance: number;
  createdAt: string;
}

export interface Transaction {
  id: string;
  fromAccountId: string;
  toAccountId?: string;
  amount: number;
  type: 'deposit' | 'withdrawal' | 'transfer';
  description: string;
  timestamp: string;
  status: 'completed' | 'pending' | 'failed';
}

export interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
}