import React, { useState } from 'react';
import { Search, Filter, Download, TrendingUp, TrendingDown, Calendar } from 'lucide-react';
import { Transaction, Account } from '../types';

interface TransactionHistoryProps {
  transactions: Transaction[];
  accounts: Account[];
  hideBalances: boolean;
}

export const TransactionHistory: React.FC<TransactionHistoryProps> = ({
  transactions,
  accounts,
  hideBalances,
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [filterType, setFilterType] = useState<'all' | 'deposit' | 'withdrawal' | 'transfer'>('all');
  const [sortOrder, setSortOrder] = useState<'newest' | 'oldest' | 'highest' | 'lowest'>('newest');

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(amount);
  };

  const getTransactionIcon = (transaction: Transaction) => {
    const isIncoming = accounts.some(acc => acc.id === transaction.toAccountId);
    return isIncoming ? TrendingUp : TrendingDown;
  };

  const getTransactionColor = (transaction: Transaction) => {
    const isIncoming = accounts.some(acc => acc.id === transaction.toAccountId);
    return isIncoming ? 'text-emerald-600' : 'text-red-600';
  };

  const getTransactionSign = (transaction: Transaction) => {
    const isIncoming = accounts.some(acc => acc.id === transaction.toAccountId);
    return isIncoming ? '+' : '-';
  };

  const filteredTransactions = transactions
    .filter(transaction => {
      const matchesSearch = transaction.description.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesFilter = filterType === 'all' || transaction.type === filterType;
      return matchesSearch && matchesFilter;
    })
    .sort((a, b) => {
      switch (sortOrder) {
        case 'newest':
          return new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime();
        case 'oldest':
          return new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime();
        case 'highest':
          return b.amount - a.amount;
        case 'lowest':
          return a.amount - b.amount;
        default:
          return 0;
      }
    });

  const totalTransactions = filteredTransactions.length;
  const totalAmount = filteredTransactions.reduce((sum, transaction) => {
    const isIncoming = accounts.some(acc => acc.id === transaction.toAccountId);
    return sum + (isIncoming ? transaction.amount : -transaction.amount);
  }, 0);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-200">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6">
          <h2 className="text-2xl font-semibold text-gray-900 mb-4 sm:mb-0">Transaction History</h2>
          <div className="flex items-center space-x-3">
            <button className="flex items-center px-4 py-2 text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">
              <Download className="w-4 h-4 mr-2" />
              Export
            </button>
          </div>
        </div>

        {/* Statistics */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
          <div className="bg-blue-50 p-4 rounded-lg">
            <p className="text-sm text-blue-600 font-medium">Total Transactions</p>
            <p className="text-2xl font-bold text-blue-900">{totalTransactions}</p>
          </div>
          <div className="bg-emerald-50 p-4 rounded-lg">
            <p className="text-sm text-emerald-600 font-medium">Net Amount</p>
            <p className="text-2xl font-bold text-emerald-900">
              {hideBalances ? '••••••' : formatCurrency(Math.abs(totalAmount))}
            </p>
          </div>
          <div className="bg-orange-50 p-4 rounded-lg">
            <p className="text-sm text-orange-600 font-medium">This Month</p>
            <p className="text-2xl font-bold text-orange-900">
              {filteredTransactions.filter(t => 
                new Date(t.timestamp).getMonth() === new Date().getMonth()
              ).length}
            </p>
          </div>
        </div>

        {/* Filters */}
        <div className="flex flex-col sm:flex-row gap-4">
          <div className="flex-1">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5" />
              <input
                type="text"
                placeholder="Search transactions..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
          </div>
          <div className="flex gap-2">
            <select
              value={filterType}
              onChange={(e) => setFilterType(e.target.value as any)}
              className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            >
              <option value="all">All Types</option>
              <option value="transfer">Transfers</option>
              <option value="deposit">Deposits</option>
              <option value="withdrawal">Withdrawals</option>
            </select>
            <select
              value={sortOrder}
              onChange={(e) => setSortOrder(e.target.value as any)}
              className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            >
              <option value="newest">Newest First</option>
              <option value="oldest">Oldest First</option>
              <option value="highest">Highest Amount</option>
              <option value="lowest">Lowest Amount</option>
            </select>
          </div>
        </div>
      </div>

      {/* Transactions List */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        {filteredTransactions.length === 0 ? (
          <div className="p-12 text-center">
            <div className="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <Calendar className="w-8 h-8 text-gray-400" />
            </div>
            <h3 className="text-lg font-medium text-gray-900 mb-2">No transactions found</h3>
            <p className="text-gray-600">
              {searchTerm || filterType !== 'all' 
                ? 'Try adjusting your search or filter criteria.' 
                : 'Your transaction history will appear here.'}
            </p>
          </div>
        ) : (
          <div className="divide-y divide-gray-200">
            {filteredTransactions.map((transaction, index) => {
              const Icon = getTransactionIcon(transaction);
              const color = getTransactionColor(transaction);
              const sign = getTransactionSign(transaction);
              
              return (
                <div key={transaction.id} className="p-6 hover:bg-gray-50 transition-colors">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center">
                      <div className={`w-12 h-12 rounded-full flex items-center justify-center mr-4 ${
                        color === 'text-emerald-600' ? 'bg-emerald-100' : 'bg-red-100'
                      }`}>
                        <Icon className={`w-6 h-6 ${color}`} />
                      </div>
                      <div>
                        <h4 className="font-medium text-gray-900">{transaction.description}</h4>
                        <div className="flex items-center text-sm text-gray-600 mt-1">
                          <span>{new Date(transaction.timestamp).toLocaleDateString()}</span>
                          <span className="mx-2">•</span>
                          <span>{new Date(transaction.timestamp).toLocaleTimeString()}</span>
                          <span className="mx-2">•</span>
                          <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                            transaction.status === 'completed' 
                              ? 'bg-green-100 text-green-800' 
                              : transaction.status === 'pending'
                              ? 'bg-yellow-100 text-yellow-800'
                              : 'bg-red-100 text-red-800'
                          }`}>
                            {transaction.status.charAt(0).toUpperCase() + transaction.status.slice(1)}
                          </span>
                        </div>
                      </div>
                    </div>
                    <div className="text-right">
                      <p className={`text-lg font-semibold ${color}`}>
                        {hideBalances ? '••••••' : `${sign}${formatCurrency(transaction.amount)}`}
                      </p>
                      <p className="text-sm text-gray-600 capitalize">{transaction.type}</p>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};