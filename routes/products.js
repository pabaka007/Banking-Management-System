import express from 'express';
import db from '../database/connection.js';
import { basicAuth } from '../middleware/auth.js';
import transactionService from '../services/transactionService.js';

const router = express.Router();

// Add product
router.post('/product', basicAuth, async (req, res) => {
    try {
        const { name, price, description } = req.body;

        if (!name || !price || price <= 0) {
            return res.status(400).json({
                error: 'Product name and positive price are required'
            });
        }

        const result = await db.query(
            'INSERT INTO products (name, price, description) VALUES ($1, $2, $3) RETURNING id',
            [name, parseFloat(price), description || '']
        );

        res.status(201).json({
            id: result.rows[0].id,
            message: 'Product added'
        });

    } catch (error) {
        console.error('Add product error:', error);
        res.status(500).json({
            error: 'Internal server error adding product'
        });
    }
});

// List all products
router.get('/product', async (req, res) => {
    try {
        const result = await db.query(
            'SELECT id, name, price, description FROM products ORDER BY created_at DESC'
        );

        res.json(result.rows);

    } catch (error) {
        console.error('List products error:', error);
        res.status(500).json({
            error: 'Internal server error fetching products'
        });
    }
});

// Buy product
router.post('/buy', basicAuth, async (req, res) => {
    try {
        const { product_id } = req.body;
        const userId = req.user.id;

        if (!product_id) {
            return res.status(400).json({
                error: 'Product ID is required'
            });
        }

        const client = await db.getClient();

        try {
            await client.query('BEGIN');

            // Get product details
            const productResult = await client.query(
                'SELECT id, name, price FROM products WHERE id = $1',
                [product_id]
            );

            if (productResult.rows.length === 0) {
                throw new Error('Invalid product');
            }

            const product = productResult.rows[0];
            const productPrice = parseFloat(product.price);

            // Get user's current balance
            const userResult = await client.query(
                'SELECT balance FROM users WHERE id = $1 FOR UPDATE',
                [userId]
            );

            const currentBalance = parseFloat(userResult.rows[0].balance);

            if (currentBalance < productPrice) {
                throw new Error('Insufficient balance');
            }

            // Update user's balance
            const newBalance = currentBalance - productPrice;
            await client.query(
                'UPDATE users SET balance = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
                [newBalance, userId]
            );

            // Create debit transaction
            await client.query(
                `INSERT INTO transactions (user_id, kind, amount, updated_balance, description, product_id)
                 VALUES ($1, 'debit', $2, $3, $4, $5)`,
                [userId, productPrice, newBalance, `Purchase: ${product.name}`, product.id]
            );

            await client.query('COMMIT');

            res.json({
                message: 'Product purchased',
                balance: newBalance
            });

        } catch (error) {
            await client.query('ROLLBACK');
            throw error;
        } finally {
            client.release();
        }

    } catch (error) {
        console.error('Purchase error:', error);
        
        if (error.message === 'Insufficient balance' || error.message === 'Invalid product') {
            return res.status(400).json({
                error: error.message
            });
        }

        res.status(500).json({
            error: 'Internal server error during purchase'
        });
    }
});

export default router;