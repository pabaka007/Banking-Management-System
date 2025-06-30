import { useState, useEffect } from 'react';
import { Account, Transaction } from '../types';

export const useAccounts = (userId: string | null) => {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (userId) {
      loadAccounts();
      loadTransactions();
    }
  }, [userId]);

  const loadAccounts = () => {
    const allAccounts = JSON.parse(localStorage.getItem('bankingAccounts') || '[]');
    const userAccounts = allAccounts.filter((acc: Account) => acc.userId === userId);
    setAccounts(userAccounts);
  };

  const loadTransactions = () => {
    const allTransactions = JSON.parse(localStorage.getItem('bankingTransactions') || '[]');
    const userTransactions = allTransactions.filter((tx: Transaction) => {
      const userAccountIds = accounts.map(acc => acc.id);
      return userAccountIds.includes(tx.fromAccountId) || 
             (tx.toAccountId && userAccountIds.includes(tx.toAccountId));
    });
    setTransactions(userTransactions.sort((a: Transaction, b: Transaction) => 
      new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
    ));
  };

  const transfer = async (fromAccountId: string, toAccountNumber: string, amount: number, description: string): Promise<boolean> => {
    setLoading(true);
    
    try {
      const allAccounts = JSON.parse(localStorage.getItem('bankingAccounts') || '[]');
      const fromAccount = allAccounts.find((acc: Account) => acc.id === fromAccountId);
      const toAccount = allAccounts.find((acc: Account) => acc.accountNumber === toAccountNumber);

      if (!fromAccount || !toAccount) {
        throw new Error('Account not found');
      }

      if (fromAccount.balance < amount) {
        throw new Error('Insufficient funds');
      }

      // Update balances
      fromAccount.balance -= amount;
      toAccount.balance += amount;

      // Save updated accounts
      localStorage.setItem('bankingAccounts', JSON.stringify(allAccounts));

      // Create transaction record
      const transaction: Transaction = {
        id: Date.now().toString(),
        fromAccountId,
        toAccountId: toAccount.id,
        amount,
        type: 'transfer',
        description,
        timestamp: new Date().toISOString(),
        status: 'completed',
      };

      const allTransactions = JSON.parse(localStorage.getItem('bankingTransactions') || '[]');
      allTransactions.push(transaction);
      localStorage.setItem('bankingTransactions', JSON.stringify(allTransactions));

      loadAccounts();
      loadTransactions();
      setLoading(false);
      return true;
    } catch (error) {
      setLoading(false);
      return false;
    }
  };

  const getTotalBalance = () => {
    return accounts.reduce((total, account) => total + account.balance, 0);
  };

  return {
    accounts,
    transactions,
    loading,
    transfer,
    getTotalBalance,
    loadAccounts,
    loadTransactions,
  };
};