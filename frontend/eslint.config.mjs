// ESLint flat config dùng chung cho cả admin-app và public-web.
// conventions.md §1.4: TypeScript strict, CẤM `any` (dùng `unknown` + narrow).
// admin-app / public-web kế thừa file này rồi thêm plugin riêng (react-hooks, next).

import js from '@eslint/js';
import prettier from 'eslint-config-prettier';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
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
    },
  },

  // ---------------------------------------------------------------------------
  // React (WS-8) — áp cho mã nguồn của cả hai app FE.
  //
  // Gộp vào file này chứ KHÔNG đặt eslint.config.mjs riêng trong từng app: ESLint 9
  // flat config chỉ đọc MỘT file cấu hình tính từ thư mục chạy lệnh, không gộp cấu
  // hình lồng nhau như .eslintrc ngày trước. Có file con thì nó bị bỏ qua im lặng —
  // lint vẫn xanh mà rule React chưa từng chạy.
  // ---------------------------------------------------------------------------
  {
    files: ['admin-app/src/**/*.{ts,tsx}', 'public-web/**/*.{ts,tsx}'],
    plugins: {
      react,
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    languageOptions: {
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    settings: { react: { version: 'detect' } },
    rules: {
      ...react.configs.flat['jsx-runtime'].rules,
      ...reactHooks.configs['recommended-latest'].rules,

      // Chỉ component RichContent (đã sanitize phía máy chủ) mới được dựng HTML thô —
      // conventions.md §4.4. Nội dung bài viết CMS đến từ trình soạn thảo, tức là đúng
      // đường một payload XSS đi vào nếu chỗ nào đó lỡ tay.
      'react/no-danger': 'error',

      // Hot-reload của Vite chỉ giữ được state khi mỗi module chỉ xuất component.
      // Cảnh báo thôi vì `--max-warnings=0` đã biến nó thành lỗi ở CI.
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
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
