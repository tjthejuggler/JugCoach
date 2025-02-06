I. Project Overview

Goal:
Build an Android-based juggling coach that leverages a legacy juggling pattern database and enhances it with dynamic performance tracking, detailed analytics, free-form conversational coaching (powered by an LLM), and flexible user-controlled (or automated) practice suggestions.

    Platforms: Android phone as the primary device and a companion Wear OS (watch) app.
    Core Interactions:
        Free-form conversational interactions (via text and voice).
        Detailed logging of practice sessions.
        Dynamic suggestions and adaptive coaching powered by an LLM (with user-configurable API keys).
        Data visualization (charts, graphs, reports) and access to all underlying notes and raw data.
    Flexibility:
        The system is designed to evolve. New fields, data points, relationships, and coaching strategies may be added over time.
        The coach will learn from both your inputs (including corrections, tag definitions, and feedback) and its own experiments.
    Data Sources:
        Legacy juggling patterns imported from an existing website (in JSON format).
        New dynamic data from your sessions, self-reports (fatigue, motivation, context like location, music, etc.), and system-generated notes.




VIII. Final Summary

    Start with a strong MVP that imports your legacy patterns, logs basic sessions, and enables a conversational interface with LLM-powered coaching.
    Iterate quickly: Use real-world usage to decide what additional metrics, data points, and coaching logic are most valuable.
    Keep the system open and flexible: New fields, dynamic suggestions, and evolving coach personalities will be integral to the long-term success of the app.
    Maintain robust documentation: Every design decision, system prompt, and data model change should be clearly documented for future developers and for when you revisit or expand the project.