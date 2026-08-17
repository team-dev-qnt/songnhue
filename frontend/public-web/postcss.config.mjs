/** Tailwind 4 chạy qua plugin PostCSS riêng (`@tailwindcss/postcss`), không còn `tailwindcss` trực tiếp. */
const config = {
  plugins: {
    '@tailwindcss/postcss': {},
  },
};

export default config;
