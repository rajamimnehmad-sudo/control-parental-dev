import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#18212f",
        line: "#d8dee8",
        canvas: "#f5f7fa",
        accent: "#176f6b",
        warning: "#9a6700",
        danger: "#b42318",
      },
      boxShadow: {
        soft: "0 12px 30px rgba(24, 33, 47, 0.08)",
      },
    },
  },
  plugins: [],
};

export default config;
