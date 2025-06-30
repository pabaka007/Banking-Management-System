import bcrypt from 'bcrypt';
import db from '../database/connection.js';

export const basicAuth = async (req, res, next) => {
    try {
        const authHeader = req.headers.authorization;
        
        if (!authHeader || !authHeader.startsWith('Basic ')) {
            return res.status(401).json({ 
                error: 'Missing or invalid authorization header' 
            });
        }

        const base64Credentials = authHeader.split(' ')[1];
        const credentials = Buffer.from(base64Credentials, 'base64').toString('ascii');
        const [username, password] = credentials.split(':');

        if (!username || !password) {
            return res.status(401).json({ 
                error: 'Invalid credentials format' 
            });
        }

        // Find user in database
        const result = await db.query(
            'SELECT id, username, password_hash FROM users WHERE username = $1',
            [username]
        );

        if (result.rows.length === 0) {
            return res.status(401).json({ 
                error: 'Invalid username or password' 
            });
        }

        const user = result.rows[0];
        const isValidPassword = await bcrypt.compare(password, user.password_hash);

        if (!isValidPassword) {
            return res.status(401).json({ 
                error: 'Invalid username or password' 
            });
        }

        // Attach user to request object
        req.user = {
            id: user.id,
            username: user.username
        };

        next();
    } catch (error) {
        console.error('Authentication error:', error);
        res.status(500).json({ 
            error: 'Internal server error during authentication' 
        });
    }
};