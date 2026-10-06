import app from './app';

const port = process.env.PORT || 3000;
if (require.main === module)
  app.listen(port, () => console.log(`nudge-engine listening on http://localhost:${port}`));

export default app;
