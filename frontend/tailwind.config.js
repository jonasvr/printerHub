/** @type {import('tailwindcss').Config} */
module.exports = {
  // Tell Tailwind where to look for class names — it removes unused styles in prod builds.
  content: [
    "./src/**/*.{html,ts}"
  ],
  theme: {
    extend: {
      // PrinterHub brand colours — tweak these to match your design
      colors: {
        brand: {
          50:  '#eff6ff',
          100: '#dbeafe',
          500: '#3b82f6',
          600: '#2563eb',
          700: '#1d4ed8',
          900: '#1e3a8a',
        }
      },
      // Printer state colours used on dashboard cards
      printState: {
        printing: '#22c55e',  // green
        paused:   '#f59e0b',  // amber
        error:    '#ef4444',  // red
        idle:     '#94a3b8',  // slate
        offline:  '#64748b',  // slate darker
      }
    }
  },
  plugins: []
}
