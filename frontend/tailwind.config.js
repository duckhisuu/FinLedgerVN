/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        fintech: {
          dark: '#0b0f19',
          card: '#111827',
          border: '#1f2937',
          primary: '#10b981',
          accent: '#6366f1',
        }
      }
    },
  },
  plugins: [],
}
