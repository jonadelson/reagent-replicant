/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.clj", "./src/**/*.cljc", "./src/**/*.cljs"],
  plugins: [
    require("daisyui")
  ],
  daisyui: {
    themes: [
      "cupcake"
    ]
  }
}
