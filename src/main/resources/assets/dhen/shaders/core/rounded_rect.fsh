#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 localPosition;
flat in vec2 halfExtent;
flat in float cornerRadius;
flat in float borderWidth;
in vec4 vertexColor;

out vec4 fragColor;

const float MINIMUM_EDGE = 0.0001;
const float PADDING = 1.0;

float roundedBoxDistance(vec2 point, vec2 extent, float radius) {
    vec2 corner = abs(point) - extent + radius;
    return length(max(corner, 0.0)) + min(max(corner.x, corner.y), 0.0) - radius;
}

void main() {
    float distance = roundedBoxDistance(localPosition, halfExtent, cornerRadius);
    if (borderWidth > 0.0) {
        distance = abs(distance + borderWidth * 0.5) - borderWidth * 0.5;
    }

    float edge = clamp(fwidth(distance) * 0.5, MINIMUM_EDGE, PADDING);
    float coverage = 1.0 - smoothstep(-edge, edge, distance);
    if (coverage <= 0.0) {
        discard;
    }

    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage) * ColorModulator;
}
