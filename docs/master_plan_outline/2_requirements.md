II. Requirements
A. Functional Requirements

    Database & Data Management
        Legacy Import:
            Import the legacy juggling patterns JSON file.
            Map existing fields (name, difficulty, siteswap, tags, prereqs, dependents, etc.) into a new, flexible data model.
        Dynamic Fields:
            Ability to add fields for session data (catches, duration, self-reported difficulty, fatigue, contextual factors, etc.).
            Support for pattern relationships (prereqs, related, dependents) to help the coach plan progression.
        Editable Notes & Tag Definitions:
            Maintain a separate “knowledge base” for tag definitions, coaching notes, session observations, and user/coach theories.
            Both the user and the coach (i.e., the system) have read/write access to these data stores.
        Import/Export:
            Provide mechanisms to export/import the full dataset (patterns, sessions, notes, etc.) for backup and external analysis.
        Data Backup & Sync:
            Store data locally (using Room) with an option to sync/back up to a cloud service (for redundancy).

    Performance Tracking & Analytics
        Session Logging:
            Log every attempt with a timestamp, pattern chosen, mode (coach-suggested vs. user-selected), duration, number of catches, and self-reported metrics (e.g., fatigue, perceived difficulty).
        Contextual Data:
            Optionally log contextual data (location, whether at a convention, alone or with others, background audio context, etc.) either by manual input or via phone permissions.
        Visualization:
            Enable on-demand generation of charts/graphs/reports summarizing:
                Total practice time.
                Performance trends (e.g., catches per session over time).
                Breakdown by pattern, tag, or contextual factors.
        Goal Setting & Feedback:
            Allow setting of goals (e.g., “increase catches on pattern X by 20% over 4 weeks”).
            The coach should track progress and note which suggestions (coach vs. user ideas) work best.

    Conversational & Adaptive Coaching
        LLM-Powered Conversation:
            A chat interface where you can interact freely with the coach. The conversation will cover:
                Pre-session planning (asking about your current state, mood, and preferences).
                During and post-session feedback.
                Casual “brainstorming” of new ideas or theories.
            The coach will maintain internal notes, ask clarifying questions, and use that data to adapt its suggestions.
        Flexible Control:
            The app should support various modes of control:
                Full control by the coach.
                Full user control.
                Hybrid modes (e.g., “I want an easier day” or “focus on certain tags”).
        Internal Logic & Experimentation:
            The LLM (using a system prompt with instructions and access to local databases) is responsible for:
                Making pattern suggestions.
                Forming new theories on progression.
                Deciding which data to track or ignore (with the user able to review and modify).
            Keep track of whether an idea or suggestion was user-generated or coach-generated for later analysis.

    Voice & Text Interaction
        Dual Input/Output Modes:
            Both text and voice should be supported.
            On the phone: a chat interface that can switch between typing and speaking.
            On the watch: a minimalistic interface for basic commands (start/stop session, quick feedback, simple queries).
        Voice Interaction Details:
            Use Android’s built-in speech-to-text for input (initially) with the possibility to upgrade to a more refined library.
            Use Android’s Text-to-Speech (TTS) engine for spoken coach responses.
        Offline/Online Modes:
            While LLM interactions require connectivity, basic logging and local data viewing should work offline. Once back online, data sync and additional analysis can occur.

    LLM API Integration & Flexibility
        User-Supplied API Key:
            Provide a settings screen where the user can enter or change the API key for the LLM service.
        Multiple Models:
            Allow the use of different API keys/models so the user can experiment with different LLMs.
        Context Management:
            Use a system prompt that details:
                How the coach can read/write to the local databases (patterns, session logs, notes, tag definitions).
                The current session state (pre-session mood, previous performance metrics, etc.).
                Guidelines on not always using the full history but summarizing the context for efficiency.

B. Non-Functional Requirements

    Flexibility & Evolvability:
        The design should accommodate future changes to the data model and coaching logic without requiring complete rewrites.
    User Privacy & Security:
        Data is primarily stored locally (via Room), with user-controlled online backups.
        No raw voice recordings need be stored unless explicitly enabled.
    Responsiveness & Usability:
        The UI must be responsive on both phone and watch.
        Voice and text interactions should be near real-time.
    Extensibility:
        The architecture should allow new modules (e.g., sensor-based start/stop detection, additional reporting tools) to be added over time.