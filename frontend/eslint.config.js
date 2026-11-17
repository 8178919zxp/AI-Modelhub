import js from "@eslint/js";
import tseslint from "typescript-eslint";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import globals from "globals";
import eslintConfigPrettier from "eslint-config-prettier";

export default tseslint.config(
  // ── Global ignores ────────────────────────────────────────────
  {
    ignores: ["dist", "node_modules", "coverage", "build"],
  },

  // ── Base JavaScript recommended rules ─────────────────────────
  js.configs.recommended,

  // ── TypeScript recommended + stylistic rules ──────────────────
  ...tseslint.configs.recommended,

  // ── React + TypeScript specific config ────────────────────────
  {
    files: ["**/*.{ts,tsx}"],
    languageOptions: {
      parserOptions: {
        project: ["./tsconfig.json"],
        tsconfigRootDir: import.meta.dirname,
      },
      globals: {
        ...globals.browser,
        ...globals.es2022,
      },
    },
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
    },
    rules: {
      // React Hooks
      ...reactHooks.configs.recommended.rules,

      // React Refresh (warn in dev)
      "react-refresh/only-export-components": [
        "warn",
        { allowConstantExport: true },
      ],

      // TypeScript
      "@typescript-eslint/no-unused-vars": [
        "warn",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
      ],
      "@typescript-eslint/consistent-type-imports": [
        "warn",
        { prefer: "type-imports" },
      ],
      "@typescript-eslint/no-explicit-any": "warn",
      "@typescript-eslint/no-floating-promises": "warn",
    },
  },

  // ── Relaxed rules for test files ──────────────────────────────
  {
    files: ["**/*.test.{ts,tsx}", "**/__tests__/**"],
    rules: {
      "@typescript-eslint/no-explicit-any": "off",
    },
  },

  // ── Prettier — disable conflicting ESLint rules ───────────────
  eslintConfigPrettier,
);
