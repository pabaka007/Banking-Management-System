import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import db from './database/connection.js';
import authRoutes from './routes/auth.js';
import walletRoutes from './routes/wallet.js';
import productRoutes from './routes/products.js';

// Load environment variables
dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));

// Request logging middleware
app.use((req, res, next) => {
    console.log(`📝 ${new Date().toISOString()} - ${req.method} ${req.path}`);
    next();
});

// Health check endpoint
app.get('/health', (req, res) => {
    res.json({ 
        status: 'healthy', 
        timestamp: new Date().toISOString(),
        service: 'Digital Wallet API'
    });
});

// API Routes
app.use('/', authRoutes);
app.use('/', walletRoutes);
app.use('/', productRoutes);

// API documentation endpoint
app.get('/', (req, res) => {
    res.json({
        message: 'Digital Wallet API',
        version: '1.0.0',
        endpoints: {
            'POST /register': 'Register a new user',
            'POST /fund': 'Fund your account (Auth required)',
            'POST /pay': 'Pay another user (Auth required)',
            'GET /bal?currency=USD': 'Check balance with optional currency conversion (Auth required)',
            'GET /stmt': 'Get transaction history (Auth required)',
            'POST /product': 'Add a product (Auth required)',
            'GET /product': 'List all products',
            'POST /buy': 'Buy a product (Auth required)'
        },
        authentication: 'Basic Auth (Authorization: Basic <base64(username:password)>)'
    });
});

// Error handling middleware
app.use((err, req, res, next) => {
    console.error('💥 Unhandled error:', err);
    res.status(500).json({
        error: 'Internal server error',
        message: process.env.NODE_ENV === 'development' ? err.message : 'Something went wrong'
    });
});

// 404 handler
app.use('*', (req, res) => {
    res.status(404).json({
        error: 'Endpoint not found',
        message: `Cannot ${req.method} ${req.originalUrl}`
    });
});

// Initialize database and start server
async function startServer() {
    try {
        // Initialize database schema
        await db.initializeSchema();
        
        // Start server
        app.listen(PORT, () => {
            console.log(`
🚀 Digital Wallet API Server is running!
📍 Port: ${PORT}
🌐 Health Check: http://localhost:${PORT}/health
📚 API Docs: http://localhost:${PORT}/
🔒 Authentication: Basic Auth required for protected routes
💳 Database: Connected to Supabase PostgreSQL
            `);
        });
    } catch (error) {
        console.error('❌ Failed to start server:', error);
        process.exit(1);
    }
}

// Graceful shutdown
process.on('SIGINT', async () => {
    console.log('\n🛑 Shutting down gracefully...');
    await db.close();
    process.exit(0);
});

process.on('SIGTERM', async () => {
    console.log('\n🛑 Shutting down gracefully...');
    await db.close();
    process.exit(0);
});

startServer();