import express from 'express';
import db from '../database/connection.js';
import { basicAuth } from '../middleware/auth.js';
import transactionService from '../services/transactionService.js';
import currencyService from '../services/currencyService.js';

const router = express.Router();

// Fund account
router.post('/fund', basicAuth, async (req, res) => {
    try {
        const { amt } = req.body;
        const userId = req.user.id;

        if (!amt || amt <= 0) {
            return res.status(400).json({
                error: 'Amount must be a positive number'
            });
        }

        // Get current balance
        const result = await db.query(
            'SELECT balance FROM users WHERE id = $1',
            [userId]
        );

        const currentBalance = parseFloat(result.rows[0].balance);
        const newBalance = currentBalance + parseFloat(amt);

        // Update balance
        await db.query(
            'UPDATE users SET balance = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
            [newBalance, userId]
        );

        // Create credit transaction
        await transactionService.createTransaction(
            userId,
            'credit',
            parseFloat(amt),
            newBalance,
            'Account funding'
        );

        res.json({
            balance: newBalance
        });

    } catch (error) {
        console.error('Fund error:', error);
        res.status(500).json({
            error: 'Internal server error during funding'
        });
    }
});

// Pay another user
router.post('/pay', basicAuth, async (req, res) => {
    try {
        const { to, amt } = req.body;
        const fromUserId = req.user.id;

        if (!to || !amt || amt <= 0) {
            return res.status(400).json({
                error: 'Recipient username and positive amount are required'
            });
        }

        if (to === req.user.username) {
            return res.status(400).json({
                error: 'Cannot pay yourself'
            });
        }

        const newBalance = await transactionService.transferFunds(
            fromUserId,
            to,
            parseFloat(amt)
        );

        res.json({
            balance: newBalance
        });

    } catch (error) {
        console.error('Payment error:', error);
        
        if (error.message === 'Insufficient funds' || error.message === 'Recipient not found') {
            return res.status(400).json({
                error: error.message
            });
        }

        res.status(500).json({
            error: 'Internal server error during payment'
        });
    }
});

// Check balance
router.get('/bal', basicAuth, async (req, res) => {
    try {
        const { currency = 'INR' } = req.query;
        const userId = req.user.id;

        // Get current balance
        const result = await db.query(
            'SELECT balance FROM users WHERE id = $1',
            [userId]
        );

        const balanceINR = parseFloat(result.rows[0].balance);
        
        if (currency.toUpperCase() === 'INR') {
            return res.json({
                balance: balanceINR,
                currency: 'INR'
            });
        }

        // Convert to requested currency
        const convertedBalance = await currencyService.convertFromINR(balanceINR, currency);

        res.json({
            balance: convertedBalance,
            currency: currency.toUpperCase()
        });

    } catch (error) {
        console.error('Balance check error:', error);
        
        if (error.message.includes('Unsupported currency')) {
            return res.status(400).json({
                error: error.message
            });
        }

        res.status(500).json({
            error: 'Internal server error during balance check'
        });
    }
});

// Transaction history
router.get('/stmt', basicAuth, async (req, res) => {
    try {
        const userId = req.user.id;
        const transactions = await transactionService.getUserTransactions(userId);

        res.json(transactions);

    } catch (error) {
        console.error('Statement error:', error);
        res.status(500).json({
            error: 'Internal server error fetching transactions'
        });
    }
});

export default router;