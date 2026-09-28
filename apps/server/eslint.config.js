const js = require('@eslint/js');
const prettier = require('eslint-config-prettier');

module.exports = [
  { ignores: ['node_modules/**', 'coverage/**'] },
  js.configs.recommended,
  {
    files: ['**/*.js'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'commonjs',
      globals: {
        Buffer: 'readonly',
        __dirname: 'readonly',
        __filename: 'readonly',
        clearTimeout: 'readonly',
        console: 'readonly',
        fetch: 'readonly',
        module: 'readonly',
        process: 'readonly',
        require: 'readonly',
        setTimeout: 'readonly',
      },
    },
    rules: {
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    },
  },
  {
    files: ['test/**/*.test.js'],
    languageOptions: {
      globals: {
        after: 'readonly',
        afterEach: 'readonly',
        before: 'readonly',
        beforeEach: 'readonly',
        describe: 'readonly',
        it: 'readonly',
      },
    },
  },
  prettier,
];
