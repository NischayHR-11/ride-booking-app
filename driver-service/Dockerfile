FROM 813361731051.dkr.ecr.ap-south-1.amazonaws.com/zeta-openjdk:2.0.0

ARG app
ARG version
ARG lastCommitHash
ARG lastCommitAuthorEmail

ENV ARTIFACT_NAME $app
ENV ARTIFACT_VERSION $version
ENV ARTIFACT_COMMIT_ABR $lastCommitHash
ENV ARTIFACT_COMMITTER $lastCommitAuthorEmail

ENV APP $app
ENV JAR_PATH "$DATA_PATH/$APP.jar"

RUN mkdir -p "$LOGS_PATH/$APP" && chown -R $USERNAME:$USERNAME "$LOGS_PATH/$APP"
COPY --chown=$USERNAME:$USERNAME ./target/$APP.jar $JAR_PATH
USER $USERNAME:$USERNAME
