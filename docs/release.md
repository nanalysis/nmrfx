# Release procedure

## Working on the next release

### JIRA

Before starting working on a new release, JIRA must be up to date. This means having the next version created as a "
Release" in the "NMR" project.

For example, `NMRfx 11.2.5`.

Then, when planning an issue or working on it, the "Fix Version" field must match the expected release. This allows
tracking a release progress, and ensures we have an up to date changelog.

### Git & Maven

On the master branch, pom files must contain the same version as the one declared in JIRA for the next planned release,
with a "-SNAPSHOT" suffix, to indicate that this isn't a definitive version, and that several builds will have the same
version number.

Keeping the same example, that would be `<version>11.2.5-SNAPSHOT</version>`

## Preparing for release

When the release content is ready and only testing remains, or when we want to start development for issues that should
not be in this release, we prepare the next one and freeze the current one.

### Jira

The next JIRA release must be created if it doesn't already exist.

The current release should be checked: are all issues merged? Otherwise, decide whether we need to wait for them to be
merged, or postpone them to the next release (adjusting the "Fix Version" field).

### Git & Maven

Create a new git branch, prefixed by "release"
Using the same example, the branch name would be `release/11.2.5`.

Right after having created this branch, pom files must be updated on master to match the next planned release, still
with "-SNAPSHOT" suffix.

### Development with release branches

Fixes for bugs discovered on the release would be merged on the `release/...` branch, while development can continue one
`master`. At the end of the release cycle, or earlier if needed, the release branch will be merged to master so that
bugfixes are integrated into the main development.

This workflow allows continuing work on `master` while ensuring that no last-minute regression are introduced to the
release.

### Releasing

Once testing finishes, we're ready to release.

### JIRA

Check JIRA to ensure that all planned issues were done, or postpone them by adjusting their "Fix Version" field. Then
close the release.

### Git & Maven

Update the pom files to remove the `-SNAPSHOT` suffix. Using the same example, that would now contains
`<version>11.2.5</version>`.

Commit the pom changes and push to the `release/...` branch. Then tag it and push the tag as well. The tag name must be
prefixed by `v` then contain the version number. For example, `v11.2.5`.

## Build & communicate

Build the archives, build the installers, and copy them. For internal uses, copy them to Teams/Sharepoint: Nanalysis /
Software Releases / NMRfx

Please keep this folder naming scheme: "YYYY-MM-DD - version". For example,
`2022-05-01 - 11.2.5`.

Then write a message to the "Nanalysis / Software releases" Teams channel, linking to the JIRA page that lists all
issues for the release as a changelog.

For external communication, please upload the installers to `nmrfx.org` website.

## Merge back to master

Once built, merge `master` to the `release/...` branch locally to solve the conflicts, keeping the master version for
the pom files. Then push and create a pull request. Once merged, the `release/...` branch should be removed: the tag
will be kept.

## Faster release cycle

This development cycle implies that we know at each moment which Jira issues will be present in which release version.
They are always in sync.

Sometimes, we may need to share intermediate releases. For those, we will not have the same level of support, and
specifically, we will not create fix releases. We want to keep the JIRA release page clean for the "normal" releases. As
such, these intermediate releases will not appear on Jira at all.

We should still create a `release/xxx` branch on git, tag it, push the tag, and merge the release branch back
to `master`. This is identical to normal releases, only the Jira steps are skipped.

To identify these releases, we can use an alphabetical suffix. For example, for a release happening between `11.2.4`
and `11.2.5`, we could use `11.2.4-a` up to `11.2.4-z`.