name: Pull request
description: Contribute code, docs or config to OmniTrade ERP
labels: ["PR"]
body:
  - type: markdown
    attributes:
      value: |
        Thanks for contributing! Please fill in the sections below.

        🔒 **Do not include** API keys, tokens, passwords or real customer data in your diff.

  - type: textarea
    id: what
    attributes:
      label: What does this PR change?
      description: Describe the change and, more importantly, *why*.
      placeholder: |
        The problem: ...
        The fix: ...
    validations:
      required: true

  - type: textarea
    id: linked
    attributes:
      label: Linked issues
      description: "Use `Fixes #123` to auto-close an issue. E.g. `Fixes #42`"
    validations:
      required: false

  - type: dropdown
    id: type
    attributes:
      label: Type of change
      options:
        - 🐛 Bug fix
        - ✨ New feature
        - ♻️ Refactor
        - 📝 Documentation
        - 🔧 Build / CI / tooling
        - ⚙️ Configuration
    validations:
      required: true

  - type: textarea
    id: testing
    attributes:
      label: How did you verify it?
      description: What did you run, and what was the result?
      placeholder: |
        mvn -pl erp-xxx-service -am test
        → Tests run: 42, Failures: 0
    validations:
      required: false

  - type: checkboxes
    id: checks
    attributes:
      label: Pre-flight checklist
      options:
        - label: mvn test passes locally
          required: true
        - label: The quality gates pass (for f in scripts/check-*.py; do python3 "$f"; done)
          required: true
        - label: I added or updated unit tests for my change
          required: false
        - label: I did NOT introduce mock/stub code into src/main
          required: true
        - label: I updated the relevant docs
          required: false
