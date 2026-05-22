/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // Updated to match project poster (teal / aqua primary)
        finnera: {
          50:  '#E6FBF9',
          100: '#C9F4EE',
          200: '#9FE7DF',
          300: '#6FD3CA',
          400: '#3DB9B0',
          500: '#16A3A3',  // primary teal (similar to poster headings)
          600: '#108589',
          700: '#0C6A6F',
          800: '#084A4D',
          900: '#053235',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
      }
    },
  },
  plugins: [],
}
