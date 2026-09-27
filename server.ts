import express from 'express';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';
import { apiRouter } from './backend/src/routes.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function startServer() {
  const app = express();
  const PORT = process.env.PORT ? parseInt(process.env.PORT, 10) : 3000;
  const isProd = process.env.NODE_ENV === 'production';

  // Body parsing middleware
  app.use(express.json({ limit: '1mb' }));
  app.use(express.urlencoded({ extended: true }));

  // Security response headers
  app.use((req, res, next) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'SAMEORIGIN');
    res.setHeader('X-XSS-Protection', '1; mode=block');
    next();
  });

  // Health check endpoint
  app.get('/health', (req, res) => {
    res.json({
      status: 'HEALTHY',
      service: 'Protect Your Financed Devices API Authority',
      timestamp: new Date().toISOString()
    });
  });

  // Mount API Gateway routes
  app.use('/api/v1', apiRouter);

  // APK Info Endpoint (Metadata, real size, build readiness)
  app.get('/api/v1/apk/info', (req, res) => {
    const candidatePaths = [
      path.resolve(__dirname, 'android-dpc/app/build/outputs/apk/debug/app-debug.apk'),
      path.resolve(__dirname, 'android-dpc/app/build/outputs/apk/release/app-release.apk'),
      path.resolve(process.cwd(), 'android-dpc/app/build/outputs/apk/debug/app-debug.apk'),
      path.resolve(process.cwd(), 'android-dpc/app/build/outputs/apk/release/app-release.apk')
    ];

    let foundApkPath: string | null = null;
    let fileSize: number | null = null;
    let modifiedAt: string | null = null;

    for (const p of candidatePaths) {
      if (fs.existsSync(p)) {
        foundApkPath = p;
        try {
          const stats = fs.statSync(p);
          fileSize = stats.size;
          modifiedAt = stats.mtime.toISOString();
        } catch (_) {}
        break;
      }
    }

    const host = req.get('host') || 'ais-dev-tm5ls67gqw2nrygmvrama2-896573692943.asia-southeast1.run.app';
    const proto = req.protocol === 'https' || req.get('x-forwarded-proto') === 'https' ? 'https' : 'http';
    const downloadUrl = `${proto}://${host}/download/app.apk`;

    res.json({
      success: true,
      data: {
        appName: 'SK Pro',
        packageName: 'com.protectfinanceddevices.app',
        versionName: '1.0.0',
        versionCode: 1,
        isBuilt: foundApkPath !== null,
        fileSizeFormatted: fileSize ? `${(fileSize / (1024 * 1024)).toFixed(2)} MB (${fileSize.toLocaleString()} bytes)` : 'Not built yet (Run Gradle/CI build)',
        fileSizeBytes: fileSize,
        modifiedAt,
        downloadUrl,
        serverBackendUrl: 'https://ais-dev-tm5ls67gqw2nrygmvrama2-896573692943.asia-southeast1.run.app',
        apkPath: foundApkPath || 'android-dpc/app/build/outputs/apk/debug/app-debug.apk',
        buildInstructions: {
          gradleCommand: './gradlew assembleDebug',
          ciWorkflow: '.github/workflows/android-build.yml',
          docFile: 'ANDROID_BUILD.md'
        }
      }
    });
  });

  // APK Download Endpoint (Task 1)
  app.get('/download/app.apk', (req, res) => {
    const candidatePaths = [
      path.resolve(__dirname, 'android-dpc/app/build/outputs/apk/debug/app-debug.apk'),
      path.resolve(__dirname, 'android-dpc/app/build/outputs/apk/release/app-release.apk'),
      path.resolve(process.cwd(), 'android-dpc/app/build/outputs/apk/debug/app-debug.apk'),
      path.resolve(process.cwd(), 'android-dpc/app/build/outputs/apk/release/app-release.apk')
    ];

    let foundApkPath: string | null = null;
    for (const p of candidatePaths) {
      if (fs.existsSync(p)) {
        foundApkPath = p;
        break;
      }
    }

    if (foundApkPath) {
      res.setHeader('Content-Type', 'application/vnd.android.package-archive');
      res.setHeader('Content-Disposition', 'attachment; filename="sk-pro.apk"');
      return res.sendFile(foundApkPath);
    }

    // Honest status response if APK has not been compiled yet in external build system
    return res.status(404).json({
      success: false,
      error: 'APK_NOT_FOUND',
      message: 'The SK Pro Android APK has not been compiled yet. Please build the Android project located at /android-dpc using Android Studio (Build > Build Bundle(s) / APK(s) > Build APK(s)) or trigger the GitHub Actions workflow (.github/workflows/android-build.yml). Once built, app-debug.apk will be served from android-dpc/app/build/outputs/apk/debug/app-debug.apk.',
      instructions: {
        buildEnvironment: 'Android Studio Hedgehog+ or GitHub Actions CI runner',
        targetModule: '/android-dpc',
        gradleCommand: './gradlew assembleDebug',
        outputPath: 'android-dpc/app/build/outputs/apk/debug/app-debug.apk',
        githubActionsWorkflow: '.github/workflows/android-build.yml'
      }
    });
  });

  // Global API error handler
  app.use((err: any, req: express.Request, res: express.Response, next: express.NextFunction) => {
    console.error('Unhandled server error:', err);
    res.status(err.status || 500).json({
      success: false,
      error: err.code || 'INTERNAL_SERVER_ERROR',
      message: err.message || 'An unexpected error occurred'
    });
  });

  // In development, mount Vite dev middleware
  if (!isProd) {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    // In production, serve built static assets from dist
    const distPath = path.resolve(__dirname, 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.resolve(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`[ProtectFinancedDevices] Server running on port ${PORT}`);
    console.log(`[ProtectFinancedDevices] API Gateway mounted at /api/v1`);
  });
}

startServer().catch(err => {
  console.error('Failed to start server:', err);
  process.exit(1);
});
