// ESLint flat config dùng chung cho cả admin-app và public-web.
// conventions.md §1.4: TypeScript strict, CẤM `any` (dùng `unknown` + narrow).
// admin-app / public-web kế thừa file này rồi thêm plugin riêng (react-hooks, next).

import js from '@eslint/js';
import prettier from 'eslint-config-prettier';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  {
    // Không lint file sinh ra hoặc phụ thuộc
    ignores: [
      '**/node_modules/**',
      '**/dist/**',
      '**/build/**',
      '**/.next/**',
      '**/out/**',
      '**/coverage/**',
      '**/*.min.js',
    ],
  },

  js.configs.recommended,
  ...tseslint.configs.recommended,

  {
    files: ['**/*.{ts,tsx,js,jsx,mjs,cjs}'],
    languageOptions: {
      ecmaVersion: 2023,
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
      },
    },
    rules: {
      // --- conventions.md §1.4 ---
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/consistent-type-imports': [
        'error',
        { prefer: 'type-imports', fixStyle: 'inline-type-imports' },
      ],
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],

      // --- Bẫy lỗi thường gặp ---
      eqeqeq: ['error', 'smart'],
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      'no-debugger': 'error',
      'no-alert': 'error',
      'prefer-const': 'error',
      'no-var': 'error',

      // --- Ràng buộc kiến trúc FE (conventions.md §1.4, §3) ---
      // FE không tự tạo HTTP client — mọi request đi qua shared/apiClient
      // (nơi gắn CSRF header, auto refresh token, unwrap envelope).
      'no-restricted-globals': [
        'error',
        {
          name: 'fetch',
          message:
            'Dùng shared/apiClient thay vì fetch trực tiếp — apiClient lo CSRF, refresh token, unwrap envelope (conventions.md §2.5).',
        },
      ],
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: 'axios',
              message:
                'Chỉ shared/apiClient được import axios — cấm tạo instance riêng (conventions.md §2.5).',
            },
            {
              name: 'moment',
              message: 'Dùng formatDateTime của shared/ (UTC+7) thay vì moment.',
            },
          ],
        },
      ],
      // Ghi chú cho WS-8: khi cài eslint-plugin-react, bật `react/no-danger` —
      // chỉ component RichContent đã sanitize server-side mới được dùng
      // dangerouslySetInnerHTML (conventions.md §4.4).
    },
  },

  // shared/apiClient là ngoại lệ hợp lệ của 2 rule trên
  {
    files: ['**/shared/apiClient*.{ts,tsx}', '**/shared/api/**'],
    rules: {
      'no-restricted-imports': 'off',
      'no-restricted-globals': 'off',
    },
  },

  // File cấu hình build không cần strict như mã sản phẩm
  {
    files: ['**/*.config.{ts,js,mjs,cjs}', '**/vite.config.ts', '**/next.config.{js,mjs}'],
    rules: {
      'no-console': 'off',
      '@typescript-eslint/no-explicit-any': 'off',
    },
  },

  // Đặt cuối cùng: tắt các rule style xung đột với Prettier
  prettier,
);
