import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#14202b",
        line: "#dde3e8",
        canvas: "#f4f6f8",
        sidebar: "#101c28",
        accent: "#137b72",
        warning: "#9a6700",
        danger: "#b42318",
      },
      boxShadow: {
        soft: "0 12px 30px rgba(24, 33, 47, 0.08)",
        panel: "0 1px 2px rgba(15, 23, 42, 0.04), 0 8px 24px rgba(15, 23, 42, 0.035)",
      },
    },
  },
  plugins: [],
};

export default config;
