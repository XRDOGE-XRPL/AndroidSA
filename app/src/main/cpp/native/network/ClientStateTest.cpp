#include <array>
#include <iostream>
#include <string>

#include "ClientState.h"

namespace {
std::array<std::string, 4> parseSummary(const std::string& summary) {
    std::array<std::string, 4> segments;
    std::size_t start = 0;

    for (std::size_t index = 0; index < segments.size() - 1; ++index) {
        const auto separator = summary.find('|', start);
        if (separator == std::string::npos) {
            return segments;
        }
        segments[index] = summary.substr(start, separator - start);
        start = separator + 1;
    }

    segments.back() = summary.substr(start);
    return segments;
}

bool expect(bool condition, const char* message) {
    if (!condition) {
        std::cerr << message << '\n';
        return false;
    }
    return true;
}
}  // namespace

int main() {
    androidsa::network::ClientState state;

    if (!expect(state.dispatchCommand("diagnostics:ok"), "Expected diagnostics command to succeed")) {
        return 1;
    }

    const auto diagnosticsSummary = parseSummary(state.summary());
    if (!expect(diagnosticsSummary[3] == "Manual diagnostics: ok", "Diagnostics command should update diagnostics summary")) {
        return 1;
    }

    if (!expect(state.dispatchCommand("diagnosticsok"), "Diagnostics-prefixed generic command should remain accepted")) {
        return 1;
    }
    const auto genericDiagnosticsSummary = parseSummary(state.summary());
    if (!expect(genericDiagnosticsSummary[3] == "Last JNI command: diagnosticsok", "Generic diagnostics-prefixed command should fall back to generic handler")) {
        return 1;
    }
    if (!expect(state.dispatchCommand("diagnostics foo:bar"), "Diagnostics keyword-tail generic command should remain accepted")) {
        return 1;
    }
    if (!expect(!state.dispatchCommand("diagnostics:   "), "Blank diagnostics value should fail")) {
        return 1;
    }

    if (!expect(state.dispatchCommand("status"), "Status command should succeed")) {
        return 1;
    }

    const auto statusSummary = parseSummary(state.summary());
    if (!expect(statusSummary[3] == "Status snapshot requested", "Status command should update diagnostics")) {
        return 1;
    }

    if (!expect(!state.dispatchCommand("ping\n"), "Commands with control characters should fail even if trim would remove them")) {
        return 1;
    }

    return 0;
}
