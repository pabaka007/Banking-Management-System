import db from '../database/connection.js';

class TransactionService {
    async createTransaction(userId, kind, amount, updatedBalance, description = null, recipientId = null, productId = null) {
        try {
            const result = await db.query(
                `INSERT INTO transactions (user_id, kind, amount, updated_balance, description, recipient_id, product_id)
                 VALUES ($1, $2, $3, $4, $5, $6, $7)
                 RETURNING *`,
                [userId, kind, amount, updatedBalance, description, recipientId, productId]
            );
            return result.rows[0];
        } catch (error) {
            console.error('Error creating transaction:', error);
            throw error;
        }
    }

    async getUserTransactions(userId, limit = 50) {
        try {
            const result = await db.query(
                `SELECT kind, amount as amt, updated_balance as updated_bal, timestamp, description
                 FROM transactions 
                 WHERE user_id = $1 
                 ORDER BY timestamp DESC 
                 LIMIT $2`,
                [userId, limit]
            );
            return result.rows;
        } catch (error) {
            console.error('Error fetching transactions:', error);
            throw error;
        }
    }

    async transferFunds(fromUserId, toUsername, amount) {
        const client = await db.getClient();
        
        try {
            await client.query('BEGIN');

            // Get sender's current balance
            const senderResult = await client.query(
                'SELECT balance FROM users WHERE id = $1 FOR UPDATE',
                [fromUserId]
            );

            if (senderResult.rows.length === 0) {
                throw new Error('Sender not found');
            }

            const senderBalance = parseFloat(senderResult.rows[0].balance);
            if (senderBalance < amount) {
                throw new Error('Insufficient funds');
            }

            // Get recipient user
            const recipientResult = await client.query(
                'SELECT id, balance FROM users WHERE username = $1 FOR UPDATE',
                [toUsername]
            );

            if (recipientResult.rows.length === 0) {
                throw new Error('Recipient not found');
            }

            const recipientId = recipientResult.rows[0].id;
            const recipientBalance = parseFloat(recipientResult.rows[0].balance);

            // Update sender's balance
            const newSenderBalance = senderBalance - amount;
            await client.query(
                'UPDATE users SET balance = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
                [newSenderBalance, fromUserId]
            );

            // Update recipient's balance
            const newRecipientBalance = recipientBalance + amount;
            await client.query(
                'UPDATE users SET balance = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
                [newRecipientBalance, recipientId]
            );

            // Create debit transaction for sender
            await client.query(
                `INSERT INTO transactions (user_id, kind, amount, updated_balance, description, recipient_id)
                 VALUES ($1, 'debit', $2, $3, $4, $5)`,
                [fromUserId, amount, newSenderBalance, `Payment to ${toUsername}`, recipientId]
            );

            // Create credit transaction for recipient
            await client.query(
                `INSERT INTO transactions (user_id, kind, amount, updated_balance, description, recipient_id)
                 VALUES ($1, 'credit', $2, $3, $4, $5)`,
                [recipientId, amount, newRecipientBalance, `Payment from sender`, fromUserId]
            );

            await client.query('COMMIT');
            return newSenderBalance;

        } catch (error) {
            await client.query('ROLLBACK');
            throw error;
        } finally {
            client.release();
        }
    }
}

export default new TransactionService();