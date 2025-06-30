import React, { useState } from 'react';
import { Send, AlertCircle, CheckCircle } from 'lucide-react';
import { Account } from '../types';

interface TransferModalProps {
  accounts: Account[];
  onTransfer: (fromAccountId: string, toAccountNumber: string, amount: number, description: string) => Promise<boolean>;
  loading: boolean;
  isModal?: boolean;
}

export const TransferModal: React.FC<TransferModalProps> = ({
  accounts,
  onTransfer,
  loading,
  isModal = false,
}) => {
  const [formData, setFormData] = useState({
    fromAccountId: accounts[0]?.id || '',
    toAccountNumber: '',
    amount: '',
    description: '',
  });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess(false);

    const amount = parseFloat(formData.amount);
    
    if (isNaN(amount) || amount <= 0) {
      setError('Please enter a valid amount');
      return;
    }

    const fromAccount = accounts.find(acc => acc.id === formData.fromAccountId);
    if (!fromAccount) {
      setError('Please select a valid account');
      return;
    }

    if (amount > fromAccount.balance) {
      setError('Insufficient funds');
      return;
    }

    if (formData.toAccountNumber.length !== 10) {
      setError('Account number must be 10 digits');
      return;
    }

    const success = await onTransfer(
      formData.fromAccountId,
      formData.toAccountNumber,
      amount,
      formData.description || 'Money transfer'
    );

    if (success) {
      setSuccess(true);
      setFormData({
        fromAccountId: accounts[0]?.id || '',
        toAccountNumber: '',
        amount: '',
        description: '',
      });
      setTimeout(() => setSuccess(false), 3000);
    } else {
      setError('Transfer failed. Please check the account number and try again.');
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value,
    });
    setError('');
  };

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(amount);
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-2">
          From Account
        </label>
        <select
          name="fromAccountId"
          value={formData.fromAccountId}
          onChange={handleChange}
          className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200"
          required
        >
          {accounts.map((account) => (
            <option key={account.id} value={account.id}>
              {account.accountType.charAt(0).toUpperCase() + account.accountType.slice(1)} - {formatCurrency(account.balance)}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-2">
          To Account Number
        </label>
        <input
          type="text"
          name="toAccountNumber"
          value={formData.toAccountNumber}
          onChange={handleChange}
          className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200"
          placeholder="Enter 10-digit account number"
          maxLength={10}
          required
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-2">
          Amount
        </label>
        <div className="relative">
          <span className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-500">$</span>
          <input
            type="number"
            name="amount"
            value={formData.amount}
            onChange={handleChange}
            className="w-full pl-8 pr-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200"
            placeholder="0.00"
            step="0.01"
            min="0.01"
            required
          />
        </div>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-2">
          Description (Optional)
        </label>
        <textarea
          name="description"
          value={formData.description}
          onChange={handleChange}
          className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200 resize-none"
          placeholder="Add a note for this transfer"
          rows={3}
        />
      </div>

      {error && (
        <div className="flex items-center p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">
          <AlertCircle className="w-5 h-5 mr-2 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {success && (
        <div className="flex items-center p-4 bg-green-50 border border-green-200 text-green-700 rounded-lg">
          <CheckCircle className="w-5 h-5 mr-2 flex-shrink-0" />
          <span>Transfer completed successfully!</span>
        </div>
      )}

      <button
        type="submit"
        disabled={loading}
        className="w-full bg-gradient-to-r from-blue-600 to-indigo-600 text-white py-3 px-4 rounded-lg font-medium hover:from-blue-700 hover:to-indigo-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 transform transition-all duration-200 hover:scale-105 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {loading ? (
          <div className="flex items-center justify-center">
            <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin mr-2"></div>
            Processing...
          </div>
        ) : (
          <div className="flex items-center justify-center">
            <Send className="w-5 h-5 mr-2" />
            Transfer Money
          </div>
        )}
      </button>
    </form>
  );
};