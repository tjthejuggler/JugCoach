IV. Detailed MVP Specification (Phase 1)
Core Features for MVP

    Basic Data Import & Display
        Import Legacy JSON:
            Create a parser to convert the legacy website’s JSON into the new Room database schema.
        Display Patterns:
            A simple list view showing juggling patterns and basic details.
        Editable Fields:
            Allow manual editing of tags, difficulty, and addition of new fields on patterns.

    Session Logging
        Manual Start/Stop Button:
            Implement a button on the phone UI (and a minimal one on the watch) to start/stop a juggling session.
        Logging Data:
            Record session start/stop times, selected pattern, number of catches, duration, and a basic self-report (e.g., “How do you feel?” prompt before/after a session).
        Basic Storage:
            Save session logs in the Room database.

    Conversational Coach UI
        Chat Interface:
            A simple conversational interface on the phone where you can type or use voice.
        LLM Integration:
            Integrate with an LLM API using a user-supplied API key.
            Provide a system prompt that includes current performance stats and guidance on how to access data.
            Allow the coach to suggest a juggling pattern for your next session.
        Command Flexibility:
            Allow free-form queries (e.g., “How did I do on my last session?” or “I want an easier pattern today”).

    Basic Analytics & Visualization
        Simple Reports:
            Generate a basic chart or report showing the number of catches or session duration over time.
        Display Dashboard:
            A screen that shows aggregated stats (e.g., total time juggling, average catches, etc.).

    Settings & API Key Management
        API Key Input:
            A settings screen for entering the LLM API key.
        Data Backup Options:
            Provide a manual “export data” function (e.g., export to JSON) for backup.