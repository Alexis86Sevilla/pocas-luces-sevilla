import { defineConfig } from 'vitest/config';

// Cap parallel test workers: on many-core Windows machines the default (one fork per core)
// starts ~30 forks at once and some time out before responding, failing the whole run.
export default defineConfig({
  test: {
    maxWorkers: 4,
  },
});
