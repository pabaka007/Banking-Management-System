import React, { useState } from 'react';
import { LogOut, CreditCard, Send, History, User, Plus, TrendingUp, TrendingDown, Eye, EyeOff } from 'lucide-react';
import { User as UserType, Account, Transaction } from '../types';
import { TransferModal } from './TransferModal';
import { TransactionHistory } from './TransactionHistory';

interface DashboardProps {
  user: UserType;
  accounts: Account[];
  transactions: Transaction[];
  onLogout: () => void;
  onTransfer: (fromAccountId: string, toAccountNumber: string, amount: number, description: string) => Promise<boolean>;
  loading: boolean;
}

export const Dashboard: React.FC<DashboardProps> = ({
  user,
  accounts,
  transactions,
  onLogout,
  onTransfer,
  loading,
}) => {
  const [activeTab, setActiveTab] = useState<'overview' | 'transfer' | 'history'>('overview');
  const [showTransferModal, setShowTransferModal] = useState(false);
  const [hideBalances, setHideBalances] = useState(false);

  const totalBalance = accounts.reduce((sum, account) => sum + account.balance, 0);
  const recentTransactions = transactions.slice(0, 5);

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(amount);
  };

  const formatAccountNumber = (accountNumber: string) => {
    return `****${accountNumber.slice(-4)}`;
  };

  const getTransactionIcon = (transaction: Transaction) => {
    const isIncoming = accounts.some(acc => acc.id === transaction.toAccountId);
    return isIncoming ? TrendingUp : TrendingDown;
  };

  const getTransactionColor = (transaction: Transaction) => {
    const isIncoming = accounts.some(acc => acc.id === transaction.toAccountId);
    return isIncoming ? 'text-emerald-600' : 'text-red-600';
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center py-6">
            <div className="flex items-center">
              <div className="w-10 h-10 bg-gradient-to-r from-blue-600 to-indigo-600 rounded-full flex items-center justify-center mr-3">
                <CreditCard className="w-6 h-6 text-white" />
              </div>
              <div>
                <h1 className="text-2xl font-bold text-gray-900">SecureBank</h1>
                <p className="text-sm text-gray-600">Welcome back, {user.fullName}</p>
              </div>
            </div>
            <div className="flex items-center space-x-4">
              <button
                onClick={() => setHideBalances(!hideBalances)}
                className="p-2 text-gray-400 hover:text-gray-600 transition-colors"
                title={hideBalances ? 'Show balances' : 'Hide balances'}
              >
                {hideBalances ? <Eye className="w-5 h-5" /> : <EyeOff className="w-5 h-5" />}
              </button>
              <button
                onClick={onLogout}
                className="flex items-center px-4 py-2 text-gray-700 hover:text-gray-900 transition-colors"
              >
                <LogOut className="w-5 h-5 mr-2" />
                Sign Out
              </button>
            </div>
          </div>
        </div>
      </header>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Navigation Tabs */}
        <div className="flex space-x-1 bg-gray-100 p-1 rounded-xl mb-8">
          {[
            { id: 'overview', label: 'Overview', icon: CreditCard },
            { id: 'transfer', label: 'Transfer', icon: Send },
            { id: 'history', label: 'History', icon: History },
          ].map((tab) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id as any)}
                className={`flex items-center px-6 py-3 rounded-lg font-medium transition-all duration-200 ${
                  activeTab === tab.id
                    ? 'bg-white text-blue-600 shadow-sm'
                    : 'text-gray-600 hover:text-gray-900'
                }`}
              >
                <Icon className="w-5 h-5 mr-2" />
                {tab.label}
              </button>
            );
          })}
        </div>

        {/* Overview Tab */}
        {activeTab === 'overview' && (
          <div className="space-y-8">
            {/* Balance Summary */}
            <div className="bg-gradient-to-r from-blue-600 to-indigo-600 rounded-2xl p-6 text-white">
              <div className="flex justify-between items-start">
                <div>
                  <p className="text-blue-200 text-sm font-medium">Total Balance</p>
                  <p className="text-3xl font-bold mt-1">
                    {hideBalances ? '••••••' : formatCurrency(totalBalance)}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-blue-200 text-sm">Active Accounts</p>
                  <p className="text-2xl font-bold">{accounts.length}</p>
                </div>
              </div>
            </div>

            {/* Accounts Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {accounts.map((account) => (
                <div
                  key={account.id}
                  className="bg-white rounded-xl p-6 shadow-sm border border-gray-200 hover:shadow-md transition-shadow"
                >
                  <div className="flex justify-between items-start mb-4">
                    <div>
                      <h3 className="text-lg font-semibold text-gray-900 capitalize">
                        {account.accountType} Account
                      </h3>
                      <p className="text-sm text-gray-600">
                        {formatAccountNumber(account.accountNumber)}
                      </p>
                    </div>
                    <div className={`w-3 h-3 rounded-full ${
                      account.accountType === 'checking' ? 'bg-blue-500' : 'bg-emerald-500'
                    }`}></div>
                  </div>
                  <div className="text-right">
                    <p className="text-2xl font-bold text-gray-900">
                      {hideBalances ? '••••••' : formatCurrency(account.balance)}
                    </p>
                    <p className="text-sm text-gray-600">Available Balance</p>
                  </div>
                </div>
              ))}
            </div>

            {/* Quick Actions */}
            <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-200">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">Quick Actions</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <button
                  onClick={() => setShowTransferModal(true)}
                  className="flex items-center justify-center p-4 bg-blue-50 hover:bg-blue-100 rounded-lg transition-colors"
                >
                  <Send className="w-6 h-6 text-blue-600 mr-3" />
                  <span className="font-medium text-blue-600">Transfer Money</span>
                </button>
                <button
                  onClick={() => setActiveTab('history')}
                  className="flex items-center justify-center p-4 bg-gray-50 hover:bg-gray-100 rounded-lg transition-colors"
                >
                  <History className="w-6 h-6 text-gray-600 mr-3" />
                  <span className="font-medium text-gray-600">View History</span>
                </button>
              </div>
            </div>

            {/* Recent Transactions */}
            {recentTransactions.length > 0 && (
              <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-200">
                <div className="flex justify-between items-center mb-4">
                  <h3 className="text-lg font-semibold text-gray-900">Recent Transactions</h3>
                  <button
                    onClick={() => setActiveTab('history')}
                    className="text-blue-600 hover:text-blue-700 text-sm font-medium"
                  >
                    View All
                  </button>
                </div>
                <div className="space-y-3">
                  {recentTransactions.map((transaction) => {
                    const Icon = getTransactionIcon(transaction);
                    return (
                      <div key={transaction.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                        <div className="flex items-center">
                          <div className={`w-10 h-10 rounded-full flex items-center justify-center mr-3 ${
                            getTransactionColor(transaction) === 'text-emerald-600' 
                              ? 'bg-emerald-100' 
                              : 'bg-red-100'
                          }`}>
                            <Icon className={`w-5 h-5 ${getTransactionColor(transaction)}`} />
                          </div>
                          <div>
                            <p className="font-medium text-gray-900">{transaction.description}</p>
                            <p className="text-sm text-gray-600">
                              {new Date(transaction.timestamp).toLocaleDateString()}
                            </p>
                          </div>
                        </div>
                        <div className="text-right">
                          <p className={`font-semibold ${getTransactionColor(transaction)}`}>
                            {getTransactionColor(transaction) === 'text-emerald-600' ? '+' : '-'}
                            {formatCurrency(transaction.amount)}
                          </p>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        )}

        {/* Transfer Tab */}
        {activeTab === 'transfer' && (
          <div className="max-w-2xl mx-auto">
            <div className="bg-white rounded-xl p-8 shadow-sm border border-gray-200">
              <h2 className="text-2xl font-semibold text-gray-900 mb-6">Transfer Money</h2>
              <TransferModal
                accounts={accounts}
                onTransfer={onTransfer}
                loading={loading}
                isModal={false}
              />
            </div>
          </div>
        )}

        {/* History Tab */}
        {activeTab === 'history' && (
          <TransactionHistory
            transactions={transactions}
            accounts={accounts}
            hideBalances={hideBalances}
          />
        )}
      </div>

      {/* Transfer Modal */}
      {showTransferModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl p-8 max-w-md w-full">
            <div className="flex justify-between items-center mb-6">
              <h2 className="text-2xl font-semibold text-gray-900">Transfer Money</h2>
              <button
                onClick={() => setShowTransferModal(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                <Plus className="w-6 h-6 transform rotate-45" />
              </button>
            </div>
            <TransferModal
              accounts={accounts}
              onTransfer={async (fromAccountId, toAccountNumber, amount, description) => {
                const success = await onTransfer(fromAccountId, toAccountNumber, amount, description);
                if (success) {
                  setShowTransferModal(false);
                }
                return success;
              }}
              loading={loading}
              isModal={true}
            />
          </div>
        </div>
      )}
    </div>
  );
};