import { createSystem, defaultConfig, defineConfig } from '@chakra-ui/react'

/**
 * Black-and-green theme.
 *
 * Two scales: `ink` for the near-black surfaces and `brand` for the green.
 * The semantic tokens below map Chakra's own roles (bg, fg, border, and the
 * brand colorPalette) onto those scales with a single value each, so the app
 * renders the same dark look regardless of the visitor's colour-mode setting.
 *
 * Components should reach for these roles — `bg.panel`, `fg.muted`,
 * `colorPalette="brand"` — rather than hard-coded colours.
 */
const config = defineConfig({
  theme: {
    tokens: {
      colors: {
        ink: {
          50: { value: '#eef1ef' },
          100: { value: '#d3d9d5' },
          200: { value: '#aab3ad' },
          300: { value: '#818d85' },
          400: { value: '#5c665f' },
          500: { value: '#3d453f' },
          600: { value: '#2b312d' },
          700: { value: '#1f2321' },
          800: { value: '#161918' },
          900: { value: '#0d0f0e' },
          950: { value: '#070808' },
        },
        // desaturated enough to sit on black without glaring
        brand: {
          50: { value: '#e8f7ee' },
          100: { value: '#c6ead6' },
          200: { value: '#9bd9b8' },
          300: { value: '#6cc697' },
          400: { value: '#46b27c' },
          500: { value: '#2f9a66' },
          600: { value: '#237c52' },
          700: { value: '#1a5f3f' },
          800: { value: '#12432d' },
          900: { value: '#0c2e1f' },
          950: { value: '#071c13' },
        },
      },
    },
    semanticTokens: {
      colors: {
        bg: {
          DEFAULT: { value: '{colors.ink.900}' },
          subtle: { value: '{colors.ink.800}' },
          muted: { value: '{colors.ink.700}' },
          emphasized: { value: '{colors.ink.600}' },
          panel: { value: '{colors.ink.800}' },
          error: { value: '#3a1512' },
          success: { value: '{colors.brand.900}' },
        },
        fg: {
          DEFAULT: { value: '{colors.ink.50}' },
          muted: { value: '{colors.ink.200}' },
          subtle: { value: '{colors.ink.300}' },
          error: { value: '#f0a29a' },
          success: { value: '{colors.brand.300}' },
        },
        border: {
          DEFAULT: { value: '{colors.ink.600}' },
          muted: { value: '{colors.ink.700}' },
          subtle: { value: '{colors.ink.700}' },
          emphasized: { value: '{colors.ink.500}' },
          error: { value: '#7d2b23' },
        },
        brand: {
          contrast: { value: '{colors.ink.950}' },
          fg: { value: '{colors.brand.300}' },
          subtle: { value: '{colors.brand.950}' },
          muted: { value: '{colors.brand.900}' },
          emphasized: { value: '{colors.brand.400}' },
          solid: { value: '{colors.brand.500}' },
          focusRing: { value: '{colors.brand.400}' },
        },
      },
    },
  },
  globalCss: {
    'html, body, #root': {
      minHeight: '100%',
      bg: 'bg',
      color: 'fg',
    },
    html: {
      // native controls and scrollbars follow the dark surfaces
      colorScheme: 'dark',
    },
    body: {
      // keeps long forms from jumping when a scrollbar appears
      scrollbarGutter: 'stable',
    },
    '*::selection': {
      bg: 'brand.muted',
      color: 'fg',
    },
  },
})

export const system = createSystem(defaultConfig, config)
