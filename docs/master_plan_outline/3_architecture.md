III. Architecture & Technology Stack

    Mobile Development
        Language & IDE:
            Kotlin using Android Studio.
        App Architecture:
            MVVM (Model-View-ViewModel) to keep UI and data logic separated.
            Modular design: separate modules for coaching (LLM interface), data storage, analytics, and UI components.

    Database Layer
        Local Storage:
            Room (SQLite abstraction) to store patterns, session logs, notes, tag definitions, and coach-generated content.
        Data Models:
            Pattern Model: Fields include legacy fields plus dynamically added ones (e.g., performance stats, additional tags).
            Session Log: Timestamps, pattern ID, duration, catches, self-reported metrics, context data.
            Notes & Theories: A flexible JSON/blob or table-based model for coach notes, conversation logs, tag definitions, and user modifications.
            Settings: To store API keys, user preferences, and flags for tracking new contextual data.

    LLM Integration
        Interface Module:
            A dedicated module that:
                Accepts a system prompt (detailing current context, data access instructions, and the coaching strategy).
                Sends queries to the user-supplied LLM API.
                Processes and displays the responses.
            Context Management:
                Mechanism for summarizing historical data and conversation history before sending queries (to limit context size).
                Guidelines for the LLM to perform read/write operations on the internal databases.
        Fallback & Offline Options:
            When no network/LLM access is available, the app should support local coaching using rule-based fallback (e.g., preconfigured suggestions based on historical performance).

    UI/UX Components
        Main Chat Screen:
            A conversational UI that supports both text input and voice input.
            Display area for the coach’s responses (with options to tap to expand charts or detailed reports).
        Dashboard & Analytics:
            Screens to view statistics, charts, and historical data.
            A dedicated “Raw Data” section where all logs and notes are visible/editable.
        Settings & Data Management:
            API key management, backup options, data import/export, and configuration for tracking (e.g., enabling/disabling contextual tracking).
        Watch App:
            Minimalistic interface for:
                Quick commands (start/stop session).
                Simple notifications or brief responses from the coach.
                Syncing with the phone app so that any feature available on the phone is accessible (albeit in a simplified form).

    Voice & Text Interaction Layer
        Speech-to-Text:
            Use Android’s built-in libraries initially.
        Text-to-Speech:
            Use Android’s TTS engine with the possibility to switch to higher-fidelity options later.
        Input Mode Toggle:
            A simple UI control to switch between text and voice modes as needed.

    Networking & Sync
        Online/Offline Management:
            The app should cache data locally and sync online when possible.
        API Communications:
            Secure handling of the API key and network requests.
            Error handling if the LLM service is unavailable.