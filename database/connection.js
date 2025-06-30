import pg from 'pg';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const { Pool } = pg;
const __dirname = path.dirname(fileURLToPath(import.meta.url));

class Database {
    constructor() {
        this.pool = new Pool({
            connectionString: process.env.DATABASE_URL,
            ssl: {
                rejectUnauthorized: false
            }
        });
    }

    async initializeSchema() {
        try {
            // Use the existing Supabase migration file
            const migrationPath = path.join(__dirname, '..', 'supabase', 'migrations', '20250622014214_divine_sun.sql');
            const schemaSQL = fs.readFileSync(migrationPath, 'utf8');
            await this.pool.query(schemaSQL);
            console.log('✅ Database schema initialized successfully');
        } catch (error) {
            console.error('❌ Error initializing database schema:', error);
            throw error;
        }
    }

    async query(text, params) {
        const start = Date.now();
        try {
            const res = await this.pool.query(text, params);
            const duration = Date.now() - start;
            console.log('🔍 Executed query', { text, duration, rows: res.rowCount });
            return res;
        } catch (error) {
            console.error('❌ Database query error:', error);
            throw error;
        }
    }

    async getClient() {
        return this.pool.connect();
    }

    async close() {
        await this.pool.end();
    }
}

export default new Database();