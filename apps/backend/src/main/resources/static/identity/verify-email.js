const token = tokenFromLocation();
removeTokenFromAddressBar();

const title = document.querySelector("#page-title");
const copy = document.querySelector("#page-copy");
const status = document.querySelector("#page-status");

if (token === null) {
  renderFailure("Invalid verification link", "Open the latest verification email from Trail or request a new one.");
} else {
  void verifyEmail(token);
}

async function verifyEmail(verificationToken) {
  try {
    const response = await fetch("/api/v1/email-verifications", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ token: verificationToken })
    });

    if (!response.ok) {
      renderFailure("Verification could not be completed", "Open the latest verification email or request a new link from Trail.");
      return;
    }

    renderSuccess("Email verified", "Your Trail account is ready. You can now sign in to Trail.");
  } catch {
    renderFailure("Trail is unreachable", "Check your connection and open the verification link again.");
  }
}

function renderSuccess(heading, message) {
  title.textContent = heading;
  copy.textContent = message;
  status.textContent = "You can close this tab and return to the Trail extension.";
  status.classList.add("success");
  status.classList.remove("failure");
}

function renderFailure(heading, message) {
  title.textContent = heading;
  copy.textContent = message;
  status.textContent = "No account details were exposed. Trail uses one-time links for this action.";
  status.classList.add("failure");
  status.classList.remove("success", "hidden");
}

function tokenFromLocation() {
  const hash = window.location.hash.startsWith("#") ? window.location.hash.slice(1) : window.location.hash;
  const params = new URLSearchParams(hash || window.location.search);
  const value = params.get("token");
  if (value === null || value.trim().length === 0) {
    return null;
  }
  return value.trim();
}

function removeTokenFromAddressBar() {
  if (window.history.replaceState) {
    window.history.replaceState(null, document.title, window.location.pathname);
  }
}
