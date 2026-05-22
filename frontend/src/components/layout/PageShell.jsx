/**
 * Consistent page chrome for role views (aligned with login / dashboard styling).
 */
export function PageShell({ title, subtitle, meta, actions, children }) {
  return (
    <div className="animate-fadeIn space-y-6">
      <div className="flex flex-col gap-4 border-b border-gray-200/90 pb-6 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-2xl font-semibold tracking-tight text-gray-900">{title}</h1>
          {subtitle && (
            <p className="mt-2 max-w-3xl text-sm leading-relaxed text-gray-500">{subtitle}</p>
          )}
        </div>
        <div className="flex shrink-0 flex-col items-stretch gap-3 sm:items-end">
          {meta}
          {actions}
        </div>
      </div>
      {children}
    </div>
  );
}

export function IllustrationNotice({ className = '' }) {
  return (
    <p
      className={`rounded-lg border border-dashed border-finnera-200/80 bg-finnera-50/80 px-3 py-2 text-center text-xs font-medium text-finnera-800 ${className}`}
    >
      Illustrative preview — values refresh when live services return data.
    </p>
  );
}
