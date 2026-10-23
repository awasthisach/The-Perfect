#!/usr/bin/env bash
# The hosted runner may own only this device; never grant world access.
# Preserve software emulation when hardware acceleration is unavailable.
set -euo pipefail
if [ -e /dev/kvm ] &&
   sudo -n chown "$(id -u):$(id -g)" /dev/kvm &&
   sudo -n chmod 600 /dev/kvm &&
   [ -r /dev/kvm ] && [ -w /dev/kvm ]; then
  printf '%s\n' on
else
  printf '%s\n' off
fi
