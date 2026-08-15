const token = tokenFromLocation();
removeTokenFromAddressBar();

const title = document.querySelector("#page-title");
const copy = document.querySelector("#page-copy");
const form = document.querySelector("#reset-form");
const newPassword = document.querySelector("#new-password");
const confirmPassword = document.querySelector("#confirm-password");
const submitButton = document.querySelector("#submit-button");
const status = document.querySelector("#page-status");

if (token === null) {
  form.classList.add("hidden");
  renderFailure("Invalid reset link", "Open the latest password-reset email from Trail or request a new one.");
} else {
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    void resetPassword(token);
  });
}

async function resetPassword(resetToken) {
  const password = newPassword.value;
  const confirmation = confirmPassword.value;

  if (password.length < 12) {
    renderInlineFailure("Use at least 12 characters.");
    newPassword.focus();
    return;
  }

  if (password !== confirmation) {
    renderInlineFailure("Passwords do not match.");
    confirmPassword.focus();
    return;
  }

  submitButton.disabled = true;
  status.classList.remove("hidden", "failure", "success");
  status.textContent = "Updating your password…";

  try {
    const response = await fetch("/api/v1/password-resets", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ token: resetToken, newPassword: password })
    });

    if (!response.ok) {
      submitButton.disabled = false;
      renderInlineFailure("We could not update your password. Check the password requirements or request a new reset link.");
      return;
    }

    form.classList.add("hidden");
    renderSuccess("Password updated", "You can now sign in to Trail using your new password.");
  } catch {
    submitButton.disabled = false;
    renderInlineFailure("Trail is unreachable. Check your connection and try again.");
  }
}

function renderSuccess(heading, message) {
  title.textContent = heading;
  copy.textContent = message;
  status.textContent = "You can close this tab and return to the Trail extension.";
  status.classList.add("success");
  status.classList.remove("failure", "hidden");
}

function renderFailure(heading, message) {
  title.textContent = heading;
  copy.textContent = message;
  status.textContent = "No account details were exposed. Trail uses one-time links for this action.";
  status.classList.add("failure");
  status.classList.remove("success", "hidden");
}

function renderInlineFailure(message) {
  status.textContent = message;
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
