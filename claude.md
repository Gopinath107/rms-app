# AI Assistant Handbook (claude.md) - Resource Management System

Welcome! This handbook is designed for Claude and other AI coding assistants working on the **Resource Management System (RMS)**. It provides a quick guide to the system's architecture, styling conventions, backend API interfaces, and critical development guidelines.

---

## 🏗️ Project Architecture Overview

The system consists of two decoupled repositories managed under a unified directory structure:

1.  **Frontend (`RMS-UI`)**: A React 19 single-page application built with Vite 8.
2.  **Backend (`rms-app`)**: A Spring Boot 3.5.5 REST API service powered by Java 21 and PostgreSQL.

```text
RMS-19/
├── RMS-UI/                 # React Frontend
│   └── src/
│       ├── components/     # UI Views & Dashboards
│       └── hooks/          # Global Hooks (e.g. useGlobalTableResizer.js)
├── rms-app/                # Java Backend
│   └── src/main/java/com/ris/rms/
│       ├── controller/     # REST Endpoints
│       └── entity/         # Hibernate JPA Entities
└── claude.md               # AI Developer Handbook
```

---

## 🚦 Port Mapping & Communication

*   **Backend Server**: Runs on port `8081` (configurable in `application.properties`).
*   **Frontend Dev Server**: Runs on port `5173` or `5174` (configured in `vite.config.js`).
*   **API Proxy**: Vite is configured to proxy all `/api` requests to `http://localhost:8081`. Thus, the React app makes requests to `/api/...` relative paths.

---

## 🎨 Frontend Styling & Component Design Rules

When modifying or creating components in the frontend:
*   **Color Themes**: The sidebar and layout themes dynamically adjust based on the logged-in user's role (defined in `App.jsx` under `ROLE_THEMES`). Respect these gradients and styles.
*   **Table Column Resizing**: Custom Excel-like column resizing is handled globally. To enable this on any table, ensure headers have the appropriate resize handler handles and layout remains `table-layout: fixed`. The `useGlobalTableResizer()` hook is executed globally at the root of `AppContent`.
*   **Premium Gradient Styling**: Any new action buttons (such as export or generate tools) should follow clean, professional solid gradient designs with subtle shadow offsets, micro-animations, and smooth hover translations (`translateY(-2px)`). Avoid outdated glassy/frosty blur effects.
*   **Toasts & Modals**: Use **Sonner** (`toast.success`/`toast.error`) for small state updates and notifications. Use **SweetAlert2** for critical confirmation dialogs.

---

## 🛡️ Backend Data & Security Rules

When modifying or creating services/controllers in the backend:
*   **Security & Auth**: Secure all endpoints behind Spring Security. Ensure they pass through the custom JWT filters.
*   **Role-Based Access**: The application uses user roles: `project-manager`, `hr`, `pmo`, `system-admin`, `portfolio-manager`, `sales-manager`, and `interview-panel`.
*   **AWS S3 Storage**: Resumes are parsed using **Apache Tika** and uploaded/retrieved from an AWS S3 bucket.
*   **Report Generation**: Excel spreadsheet generation is done via **Apache POI** within specific services.

---

## 🔒 Security & Privacy Enforcement

*   ⚠️ **No Sensitive Credentials**: Never commit or document real passwords, database credentials, SMTP credentials, JWT secret keys, AWS access keys, or secret keys. Always use clean sample environment variables or placeholders in code files, configs, and markdown docs.
*   **Database Schema**: Database schema changes are auto-applied by Hibernate using `spring.jpa.hibernate.ddl-auto=update` in the `rms` schema. Ensure database integrity and keep names consistent with existing JPA entities.

---

## 🏃 Local Quick Commands

### Frontend (`RMS-UI`):
```bash
npm install     # Install dependencies
npm run dev     # Run locally in development mode
npm run build   # Build for production
```

### Backend (`rms-app`):
```bash
mvn clean install   # Compile and package
mvn spring-boot:run # Start Spring Boot app
```
