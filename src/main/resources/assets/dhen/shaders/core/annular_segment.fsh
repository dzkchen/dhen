#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 localPosition;
flat in vec2 radii;
flat in vec2 angles;
in vec4 vertexColor;

out vec4 fragColor;

const float MINIMUM_EDGE = 0.0001;
const float PADDING = 1.0;
const float HALF_PI = 1.570796327;
const float TAU = 6.283185307;
const float FULL_SWEEP_EPSILON = 0.00025;

float wrapped(float angle) {
    return mod(angle + TAU, TAU);
}

float angularDistance(float radius) {
    if (angles.y >= TAU - FULL_SWEEP_EPSILON) {
        return -PADDING;
    }

    float relative = wrapped(atan(localPosition.y, localPosition.x) - angles.x);
    bool inside = relative <= angles.y;
    float gap = inside
        ? min(relative, angles.y - relative)
        : min(relative - angles.y, TAU - relative);
    float distance = radius * sin(min(gap, HALF_PI));
    return inside ? -distance : distance;
}

void main() {
    float radius = length(localPosition);
    float radialDistance = radius - radii.y;
    if (radii.x > 0.0) {
        radialDistance = max(radii.x - radius, radialDistance);
    }
    float distance = max(radialDistance, angularDistance(radius));
    float edge = clamp(fwidth(distance) * 0.5, MINIMUM_EDGE, PADDING);
    float coverage = 1.0 - smoothstep(-edge, edge, distance);
    if (coverage <= 0.0) {
        discard;
    }

    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage) * ColorModulator;
}
