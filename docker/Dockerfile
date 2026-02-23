# Multi-stage build: install fonts + native libs for java.awt keyboard rendering
FROM registry.access.redhat.com/ubi9/ubi-minimal:latest AS font-installer
RUN microdnf install -y fontconfig dejavu-sans-fonts && \
    mkdir -p /var/cache/fontconfig && \
    fc-cache -f && \
    microdnf clean all

FROM quay.io/keycloak/keycloak:26.5.3

USER root

# Fonts and fontconfig data
COPY --from=font-installer /usr/share/fonts /usr/share/fonts
COPY --from=font-installer /usr/share/fontconfig /usr/share/fontconfig
COPY --from=font-installer /etc/fonts /etc/fonts
COPY --from=font-installer /var/cache/fontconfig /var/cache/fontconfig

# The base image does NOT ship libfontconfig / libfreetype on all arches.
# Copy them plus all their transitive dependencies from the builder.
COPY --from=font-installer /usr/lib64/libfontconfig.so*    /usr/lib64/
COPY --from=font-installer /usr/lib64/libfreetype.so*      /usr/lib64/
COPY --from=font-installer /usr/lib64/libxml2.so*          /usr/lib64/
COPY --from=font-installer /usr/lib64/libbz2.so*           /usr/lib64/
COPY --from=font-installer /usr/lib64/libpng16.so*         /usr/lib64/
COPY --from=font-installer /usr/lib64/libharfbuzz.so*      /usr/lib64/
COPY --from=font-installer /usr/lib64/libbrotlidec.so*     /usr/lib64/
COPY --from=font-installer /usr/lib64/libbrotlicommon.so*  /usr/lib64/
COPY --from=font-installer /usr/lib64/libgraphite2.so*     /usr/lib64/
COPY --from=font-installer /usr/lib64/liblzma.so*          /usr/lib64/
COPY --from=font-installer /usr/lib64/libglib-2.0.so*      /usr/lib64/
COPY --from=font-installer /usr/lib64/libstdc++.so*        /usr/lib64/
COPY --from=font-installer /usr/lib64/libpcre2-8.so*       /usr/lib64/

# Remove stale fontconfig.properties if a previous build left one
RUN rm -f /usr/lib/jvm/*/lib/fontconfig.properties 2>/dev/null || true

USER keycloak

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
