import axios from 'axios';

class CurrencyService {
    constructor() {
        this.baseURL = 'https://api.currencyapi.com/v3';
        this.apiKey = process.env.CURRENCY_API_KEY;
    }

    async convertFromINR(amount, targetCurrency) {
        try {
            if (targetCurrency.toUpperCase() === 'INR') {
                return amount;
            }

            // Using a fallback conversion rate if API key is not available
            if (!this.apiKey || this.apiKey === 'your_currency_api_key_here') {
                console.warn('⚠️ Using fallback currency conversion rates');
                return this.getFallbackConversion(amount, targetCurrency);
            }

            const response = await axios.get(`${this.baseURL}/latest`, {
                params: {
                    apikey: this.apiKey,
                    base_currency: 'INR',
                    currencies: targetCurrency.toUpperCase()
                }
            });

            const rate = response.data.data[targetCurrency.toUpperCase()]?.value;
            if (!rate) {
                throw new Error(`Unsupported currency: ${targetCurrency}`);
            }

            return parseFloat((amount * rate).toFixed(2));
        } catch (error) {
            console.error('Currency conversion error:', error.message);
            // Fallback to approximate rates
            return this.getFallbackConversion(amount, targetCurrency);
        }
    }

    getFallbackConversion(amount, targetCurrency) {
        // Approximate conversion rates (as of 2024)
        const rates = {
            'USD': 0.012,
            'EUR': 0.011,
            'GBP': 0.0095,
            'JPY': 1.8,
            'AUD': 0.018,
            'CAD': 0.016
        };

        const rate = rates[targetCurrency.toUpperCase()];
        if (!rate) {
            throw new Error(`Unsupported currency: ${targetCurrency}`);
        }

        return parseFloat((amount * rate).toFixed(2));
    }
}

export default new CurrencyService();